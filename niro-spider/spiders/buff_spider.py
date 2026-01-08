import json
import os
import pendulum
import pydash
import requests
from http.cookies import SimpleCookie
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

from config import settings
from utils.logger import get_logger
from utils.exception_handler import handle_api_error, LoginRequiredError
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies
from utils.network_util import log_request_ip
from dto.buff_dto import BuffSellOrderResponse, ParsedBuffItemDTO

logger = get_logger(__name__)


class BuffSpider:
    def __init__(self, user_id=None):
        self.host = "https://buff.163.com"
        self.user_id = user_id
        # 绑定用户上下文，方便日志追踪
        self.logger = logger.bind(user_id=user_id)
        self.proxies = get_proxies()
        if self.proxies:
            self.logger.info(f"🛰️ BuffSpider 已启用代理: {self.proxies.get('http')}")
        
        # 初始化时随机生成一个浏览器指纹并绑定 Cookie
        self.profile = BrowserHelper.create_profile(self.user_id)
        self.logger.info(f"🎭 已为当前任务分配指纹: {self.profile.user_agent}")

    def refresh_cookie(self):
        """从数据库刷新指定用户的有效 Cookie"""
        from utils.cookie_util import get_latest_cookie
        new_cookie = get_latest_cookie(self.user_id)
        if new_cookie != self.profile.cookie:
            self.profile.update_cookie(new_cookie)
            self.logger.info(f"🔄 [Cookie] 已为用户 {self.user_id if self.user_id else 'Global'} 加载最新 Cookie")
        elif not self.profile.cookie:
            self.logger.warning(f"⚠️ 无法加载用户 {self.user_id} 的有效 Cookie")

    def refresh_profile(self):
        """重新生成浏览器指纹 Profile (通常用于任务启动时)"""
        self.profile = BrowserHelper.create_profile(self.user_id)
        self.logger.info(f"🎭 已重新分配浏览器指纹: {self.profile.user_agent}")

    @handle_api_error(default_return=[])
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type(requests.exceptions.RequestException),
        reraise=True
    )
    def get_goods_list(self, goods_id, page_num=1):
        """
        获取饰品列表
        :param goods_id: 饰品ID
        :param page_num: 页码
        :return: 解析后的数据列表
        """
        url = "/api/market/goods/sell_order"
        params = {
            "game": "csgo",
            "goods_id": goods_id,
            "page_num": page_num,
        }

        # self.logger.info(f"正在爬取 goods_id={goods_id}, page={page_num}")
        # log_request_ip(self.proxies, prefix=f"[GoodsList] ")
        response = requests.get(
            self.host + url, headers=self.profile.get_headers(), params=params, proxies=self.proxies, timeout=10
        )
        
        # 强制设置编码，防止中文乱码
        response.encoding = 'utf-8'
        response.raise_for_status()
        
        # 使用 Pydantic 解析响应
        try:
            # 优先使用 json() 解析更安全
            resp_json = response.json()
            resp_data = BuffSellOrderResponse.model_validate(resp_json)
            return self._parse_data_v2(resp_data)
        except Exception as e:
            self.logger.error(f"解析 Buff 响应失败: {e}")
            # 如果 json 解析失败，尝试原始文本
            try:
                resp_data = BuffSellOrderResponse.model_validate_json(response.text)
                return self._parse_data_v2(resp_data)
            except Exception as e2:
                self.logger.error(f"解析 Buff 响应最终失败: {e2}")
                return []

    def _parse_data_v2(self, resp: BuffSellOrderResponse):
        """使用 Pydantic 模型进行解析的改进版"""
        if resp.code == "Login Required":
            self.logger.error("🔑 Cookie 已失效或未登录")
            raise LoginRequiredError("Buff Login Required")
            
        if resp.code != "OK" or not resp.data:
            self.logger.warning(f"响应数据异常: {resp.msg}")
            return []

        data = resp.data
        parsed_items = []
        
        for item in data.items:
            goods_info = data.goods_infos.get(str(item.goods_id), {})
            user_info = data.user_infos.get(str(item.user_id), {})
            created_at_dt = pendulum.from_timestamp(item.created_at)

            parsed_item = ParsedBuffItemDTO(
                id=item.id,
                goods_id=item.goods_id,
                name=goods_info.name if goods_info else "",
                price_usd=goods_info.steam_price if goods_info else "",
                price_cny=goods_info.steam_price_cny if goods_info else "",
                paintwear=item.asset_info.paintwear,
                price_buff=item.price,
                sell_min_price=float(goods_info.sell_min_price) if goods_info else 0.0,
                buy_max_price=float(goods_info.buy_max_price) if goods_info else 0.0,
                sell_num=goods_info.sell_num if goods_info else 0,
                user_id=item.user_id,
                user_nickname=user_info.nickname if user_info else "",
                created_at=created_at_dt.to_datetime_string(),
                crawled_at=pendulum.now().to_datetime_string(),
                rarity=item.asset_info.rarity,
                exterior=item.asset_info.exterior,
                stickers=item.asset_info.stickers
            )
            parsed_items.append(parsed_item.model_dump())

        return parsed_items

    def _parse_data(self, data):
        """保留旧版解析方法以防万一，但建议使用 _parse_data_v2"""
        if not data:
            return []
            
        code = pydash.get(data, "code")
        if code == "Login Required":
            self.logger.error("🔑 Cookie 已失效或未登录")
            raise LoginRequiredError("Buff Login Required")
            
        if code != "OK" or "data" not in data:
            self.logger.warning(f"响应数据异常: {json.dumps(data, ensure_ascii=False)}")
            return []

        data_content = data["data"]
        goods_list = pydash.get(data_content, "items", [])
        goods_infos = pydash.get(data_content, "goods_infos", {})
        user_infos = pydash.get(data_content, "user_infos", {})

        parsed_items = []
        for item in goods_list:
            goods_id = item.get("goods_id", 0)
            goods_info = goods_infos.get(str(goods_id), {})
            asset_info = item.get("asset_info", {})
            user_id = item.get("user_id", 0)
            user_info = user_infos.get(str(user_id), {})
            
            created_at = pendulum.from_timestamp(item.get("created_at", 0))

            parsed_item = {
                "id": item.get("id", 0),
                "goods_id": goods_id,
                "name": goods_info.get("name", ""),
                "price_usd": goods_info.get("steam_price", ""),
                "price_cny": goods_info.get("steam_price_cny", ""),
                "paintwear": asset_info.get("paintwear"),
                "price_buff": item.get("price"),
                "sell_min_price": goods_info.get("sell_min_price", 0),
                "buy_max_price": goods_info.get("buy_max_price", 0),
                "sell_num": goods_info.get("sell_num", 0),
                "user_id": user_id,
                "user_nickname": user_info.get("nickname", ""),
                "created_at": created_at.to_datetime_string(),
                "crawled_at": pendulum.now().to_datetime_string(),
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
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        csrf_token = self._get_csrf_token()
        headers.update({
            "X-CSRFToken": csrf_token,
            "Content-Type": "application/json",
        })
        
        logger.info(f"🛒 [发起购买] POST {url} | GoodsID={goods_id} | ItemID={item_id} | Price={price_str} | PayMethod={pay_method}")
        log_request_ip(self.proxies, prefix=f"[BuyOrder] ")
        
        response = requests.post(self.host + url, headers=headers, json=payload, proxies=self.proxies, timeout=10)
        
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
            cookie.load(self.profile.cookie or "")
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
