import datetime
import json
import os
import requests
import pandas as pd
from config import settings
from utils.logger import get_logger


logger = get_logger(__name__)


class BuffSpider:
    def __init__(self):
        self.host = "https://buff.163.com"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
            "cookie": settings.BUFF_COOKIE,
        }

    def get_goods_list(self, goods_id, page_num=1):
        """
        获取饰品列表
        :param goods_id: 饰品ID
        :param page_num: 页码
        :return: 解析后的数据列表
        """
        # --- 模拟测试代码 START ---
        # 强制返回一个模拟的低价完美磨损商品，确保 TaskScanner 能扫到并下单
        import random
        mock_item = {
            "id": f"MOCK_SELL_ORDER_{random.randint(1000, 9999)}",
            "goods_id": goods_id,
            "name": "模拟测试商品 (必被扫到)",
            "price_usd": "1.00",
            "price_cny": "7.00",
            "paintwear": "0.001",  # 极低磨损
            "price_buff": "0.01",  # 极低价格
            "user_id": "MOCK_USER",
            "user_nickname": "测试卖家",
            "created_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "crawled_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        logger.info(f"🐛 [测试模式] 已注入模拟商品: 价格=0.01, 磨损=0.001")
        return [mock_item]
        # --- 模拟测试代码 END ---

        # url = "/api/market/goods/sell_order"
        # params = {
        #     "game": "csgo",
        #     "goods_id": goods_id,
        #     "page_num": page_num,
        # }
        #
        # try:
        #     logger.info(f"正在爬取 goods_id={goods_id}, page={page_num}")
        #     response = requests.get(
        #         self.host + url, headers=self.headers, params=params, timeout=10
        #     )
        #     response.raise_for_status()
        #     data = response.json()
        #     return self._parse_data(data)
        # except Exception as e:
        #     logger.error(f"❌ 请求失败: {e}", exc_info=True)
        #     return []

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
                "user_id": user_id,
                "user_nickname": user_info.get("nickname", ""),
                "created_at": created_at.strftime("%Y-%m-%d %H:%M:%S"),
                "crawled_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            }
            parsed_items.append(parsed_item)

        return parsed_items

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
        # --- 模拟测试代码 START ---
        import time
        logger.info(f"🛒 [模拟购买] 正发起虚拟下单... GoodsID={goods_id}, Price={price_str}")
        time.sleep(0.5) # 模拟网络延迟
        mock_order_id = f"MOCK_ORDER_{int(time.time())}"
        logger.info(f"✅ [模拟成功] 下单成功! 虚拟订单号: {mock_order_id}, 状态: 支付成功")
        return {
            "id": mock_order_id,
            "state_text": "支付成功",
            "price": price_str
        }
        # --- 模拟测试代码 END ---

        # url = "/api/market/goods/buy"
        # payload = {
        #     "game": "csgo",
        #     "goods_id": goods_id, 
        #     "sell_order_id": item_id,
        #     "price": float(price_str),
        #     "pay_method": pay_method,
        #     "allow_tradable_cooldown": allow_tradable_cooldown,
        #     "token": self._get_csrf_token()
        # }
        # 
        # # 补充 Headers
        # headers = self.headers.copy()
        # headers["X-CSRFToken"] = self._get_csrf_token()
        # headers["Content-Type"] = "application/json"
        # headers["Referer"] = f"https://buff.163.com/goods/{goods_id}?from=market"
        # 
        # logger.info(f"🛒 [发起购买] POST {url} Price={price_str} PayMethod={pay_method}")
        # 
        # try:
        #     response = requests.post(self.host + url, headers=headers, json=payload, timeout=10)
        #     if response.status_code != 200:
        #         logger.error(f"❌ 购买请求失败: HTTP {response.status_code} - {response.text}")
        #         return None
        #         
        #     res_json = response.json()
        #     if res_json.get("code") == "OK":
        #         data = res_json.get("data", {})
        #         order_id = data.get("id")
        #         state = data.get("state_text", "未知")
        #         logger.info(f"✅ 下单成功! 订单号: {order_id}, 状态: {state}")
        #         return data
        #     else:
        #         logger.error(f"❌ 下单失败: {res_json.get('code')} - {res_json.get('msg')}")
        #         return None
        #         
        # except Exception as e:
        #     logger.error(f"❌ 购买异常: {e}", exc_info=True)
        #     return None

    def _get_csrf_token(self):
        """从 Cookie 中提取 CSRF Token"""
        if "csrf_token=" in self.headers["cookie"]:
            try:
                return self.headers["cookie"].split("csrf_token=")[1].split(";")[0]
            except:
                pass
        return ""


if __name__ == "__main__":
    # 测试代码
    spider = BuffSpider()
    items = spider.get_goods_list(1116002)
    print(json.dumps(items, ensure_ascii=False, indent=2))
