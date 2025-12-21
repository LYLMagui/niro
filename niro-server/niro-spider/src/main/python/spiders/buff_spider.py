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
        url = "/api/market/goods/sell_order"
        params = {
            "game": "csgo",
            "goods_id": goods_id,
            "page_num": page_num,
        }

        try:
            logger.info(f"正在爬取 goods_id={goods_id}, page={page_num}")
            response = requests.get(
                self.host + url, headers=self.headers, params=params, timeout=10
            )
            response.raise_for_status()
            data = response.json()
            return self._parse_data(data)
        except Exception as e:
            logger.error(f"❌ 请求失败: {e}", exc_info=True)
            return []

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


if __name__ == "__main__":
    # 测试代码
    spider = BuffSpider()
    items = spider.get_goods_list(1116002)
    print(json.dumps(items, ensure_ascii=False, indent=2))
