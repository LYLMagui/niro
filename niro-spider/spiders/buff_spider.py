import datetime
import json
import os
import requests
from http.cookies import SimpleCookie
from config import settings
from storage.postgres_pool import PostgresPool
from utils.logger import get_logger
from utils.exception_handler import handle_api_error


logger = get_logger(__name__)


class BuffSpider:
    def __init__(self, user_id=None):
        self.host = "https://buff.163.com"
        self.pg_pool = PostgresPool()
        self.user_id = user_id
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
            "cookie": settings.BUFF_COOKIE, # 默认先使用配置文件的
        }
        # 如果指定了用户 ID，则初始化时刷新该用户的 Cookie
        if self.user_id:
            self.refresh_cookie()

    def refresh_cookie(self):
        """从数据库刷新指定用户的有效 Cookie"""
        try:
            with self.pg_pool.get_cursor() as cur:
                # 严格根据 user_id 获取对应的配置
                if self.user_id:
                    sql = "SELECT buff_cookie FROM user_buff_settings WHERE user_id = %s"
                    params = (self.user_id,)
                else:
                    # 如果没有 user_id (通常是测试或全局模式)，获取最新的一条
                    sql = "SELECT buff_cookie FROM user_buff_settings ORDER BY update_time DESC LIMIT 1"
                    params = ()

                cur.execute(sql, params)
                row = cur.fetchone()
                if row and row.get('buff_cookie'):
                    new_cookie = row['buff_cookie']
                    if new_cookie != self.headers.get("cookie"):
                        self.headers["cookie"] = new_cookie
                        logger.info(f"🔄 [Cookie] 已为用户 {self.user_id if self.user_id else 'Global'} 加载最新 Cookie")
                else:
                    logger.warning(f"⚠️ 数据库中未找到用户 {self.user_id} 的有效 Cookie")
        except Exception as e:
            logger.error(f"❌ 从数据库刷新 Cookie 失败: {e}")

    @handle_api_error(default_return=[])
    def get_goods_list(self, goods_id, page_num=1):
        """
        获取饰品列表
        :param goods_id: 饰品ID
        :param page_num: 页码
        :return: 解析后的数据列表
        """
        # --- 模拟测试代码 START (已禁用) ---
        # 强制返回一个模拟的低价完美磨损商品，确保 TaskScanner 能扫到并下单
        # import random
        # mock_item = {
        #     "id": f"MOCK_SELL_ORDER_{random.randint(1000, 9999)}",
        #     "goods_id": goods_id,
        #     "name": "模拟测试商品 (必被扫到)",
        #     "price_usd": "1.00",
        #     "price_cny": "7.00",
        #     "paintwear": "0.001",  # 极低磨损
        #     "price_buff": "0.01",  # 极低价格
        #     "user_id": "MOCK_USER",
        #     "user_nickname": "测试卖家",
        #     "created_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        #     "crawled_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        # }
        # logger.info(f"🐛 [测试模式] 已注入模拟商品: 价格=0.01, 磨损=0.001")
        # return [mock_item]
        # --- 模拟测试代码 END ---

        url = "/api/market/goods/sell_order"
        params = {
            "game": "csgo",
            "goods_id": goods_id,
            "page_num": page_num,
        }

        logger.info(f"正在爬取 goods_id={goods_id}, page={page_num}")
        response = requests.get(
            self.host + url, headers=self.headers, params=params, timeout=10
        )
        response.raise_for_status()
        data = response.json()
        return self._parse_data(data)

    def _parse_data(self, data):
        if not data or data.get("code") != "OK" or "data" not in data:
            logger.warning(f"响应数据异常: {json.dumps(data, ensure_ascii=False)}")
            return []

        goods_list = data["data"].get("items", [])
        goods_infos = data["data"].get("goods_infos", {})
        user_infos = data["data"].get("user_infos", {})

        parsed_items = []
        for item in goods_list:
            goods_id = item.get("goods_id", 0)
            goods_info = goods_infos.get(str(goods_id), {})
            asset_info = item.get("asset_info", {})
            user_id = item.get("user_id", 0)
            user_info = user_infos.get(user_id, {})
            created_at = datetime.datetime.fromtimestamp(item.get("created_at", 0))

            parsed_item = {
                "id": item.get("id", 0),
                "goods_id": goods_id,
                "name": goods_info.get("name", ""),
                "price_usd": goods_info.get("steam_price", ""),
                "price_cny": goods_info.get("steam_price_cny", ""),
                "paintwear": asset_info.get("paintwear", None),
                "price_buff": item.get("price", None),
                "sell_min_price": goods_info.get("sell_min_price", 0),
                "buy_max_price": goods_info.get("buy_max_price", 0),
                "sell_num": goods_info.get("sell_num", 0),
                "user_id": user_id,
                "user_nickname": user_info.get("nickname", ""),
                "created_at": created_at.strftime("%Y-%m-%d %H:%M:%S"),
                "crawled_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            }
            parsed_items.append(parsed_item)

        return parsed_items

    @handle_api_error(default_return=None)
    def buy(self, goods_id, item_id, price_str, pay_method=44, allow_tradable_cooldown=0):
        """
        下单购买接口
        :param goods_id: 饰品ID (如 33852)
        :param item_id: 商品具体Item ID (如 sell_order_id)
        :param price_str: 购买价格 (字符串)
        :param pay_method: 支付方式 (44=余额/网易支付, 3=支付宝)
        :param allow_tradable_cooldown: 是否允许冷却
        :return: 成功返回订单数据(dict)，失败返回 None
        """
        url = "/api/market/goods/buy"
        # 确保 item_id 是字符串
        sell_order_id = str(item_id)
        
        payload = {
            "game": "csgo",
            "goods_id": int(goods_id),
            "sell_order_id": sell_order_id,
            "price": float(price_str),
            "pay_method": int(pay_method),
            "allow_tradable_cooldown": int(allow_tradable_cooldown),
        }
        
        # 补充 Headers
        headers = self.headers.copy()
        csrf_token = self._get_csrf_token()
        headers.update({
            "X-CSRFToken": csrf_token,
            "X-Requested-With": "XMLHttpRequest",
            "Content-Type": "application/json",
            "Referer": f"https://buff.163.com/goods/{goods_id}?from=market"
        })
        
        logger.info(f"🛒 [发起购买] POST {url} | GoodsID={goods_id} | ItemID={item_id} | Price={price_str} | PayMethod={pay_method}")
        
        response = requests.post(self.host + url, headers=headers, json=payload, timeout=10)
        
        if response.status_code != 200:
            logger.error(f"❌ 下单请求失败, HTTP状态码: {response.status_code}, 内容: {response.text}")
            return None

        res_json = response.json()
        if res_json.get("code") == "OK":
            data = res_json.get("data", {})
            order_id = data.get("id")
            state = data.get("state_text", "未知")
            pay_url = data.get("pay_url")
            logger.info(f"✅ 下单成功! 订单号: {order_id}, 状态: {state}")
            if pay_url:
                logger.info(f"🔗 支付链接: {pay_url}")
            return data
        else:
            # 返回完整的 JSON 结果，以便上层判断错误类型（如：该饰品暂不支持此支付方式）
            error_msg = res_json.get("error") or res_json.get("msg") or "未知错误"
            res_json["error_msg"] = error_msg # 方便统一获取
            logger.error(f"❌ 下单失败: {res_json.get('code')} - {error_msg}")
            return res_json # 返回整个响应用于逻辑判断

    def _get_csrf_token(self):
        """从 Cookie 中提取 CSRF Token"""
        try:
            cookie = SimpleCookie()
            cookie.load(self.headers.get("cookie", ""))
            if "csrf_token" in cookie:
                token = cookie["csrf_token"].value
                # logger.debug(f"Extracted CSRF Token: {token[:10]}...")
                return token
        except Exception as e:
            logger.warning(f"解析 CSRF Token 失败: {e}")
        
        logger.warning("⚠️ 未能在 Cookie 中找到 csrf_token，下单可能会失败！")
        return ""


if __name__ == "__main__":
    # 测试代码
    spider = BuffSpider()
    items = spider.get_goods_list(1116002)
    print(json.dumps(items, ensure_ascii=False, indent=2))
