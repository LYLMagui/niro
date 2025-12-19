import calendar
import datetime
import time
import json
from dateutil import parser
import pytz
import requests
import pandas as pd
import os

class BuffGoodsSpider:
    def __init__(self, cookie=None):
        self.host = "https://buff.163.com"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
            "cookie": cookie,
        }
        self.output_dir = "buff_goods_output"
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)

    def get_goods_list(self, url, params):
        try:
            response = requests.get(
                self.host + url, headers=self.headers, params=params, timeout=10
            )
            response.raise_for_status()
            data = response.json()
            return data
        except requests.exceptions.RequestException as e:
            print(f"发送请求失败：{e}")

    def save_response(self, filename, data):
        try:
            file_path = (
                self.output_dir
                + "/"
                + filename
                + f"_{datetime.datetime.now().strftime('%Y-%m-%d_%H_%M_%S')}.json"
            )
            print(f"保存路径：{file_path}")
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
                print(f"✅ 保存成功: {filename}")
                return file_path
        except Exception as e:
            print(f"❌ 保存失败: {file_path} - {e}")

    def parse_response_data(self, file_path):
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not data or data["code"] != "OK" or "data" not in data:
            return []
        goods_list = data["data"].get("items", [])
        goods_infos = data["data"].get("goods_infos", {})
        users_infos = data["data"].get("user_infos", {})
        export_datas = []
        for item in goods_list:
            goods_id = item.get('goods_id',0)
            goods_info = goods_infos.get(str(goods_id),{})
            asset_info = item.get("asset_info",{})
            user_id = item.get("user_id",0)
            user_info = users_infos.get(user_id,{})
            created_at = datetime.datetime.fromtimestamp(item.get("created_at",0))
            export_data = {
                "ID":item.get("id",0),
                "商品id":goods_id,
                "商品名称":goods_info.get("name",""),
                "商品价格（美元）":goods_info.get("steam_price",""),
                "商品价格（人民币）":goods_info.get("steam_price_cny",""),
                "磨损值":asset_info.get("paintwear",None),
                "商品价格（buff）":item.get("price",None),
                "用户id":user_id,
                "用户昵称":user_info.get("nickname",""),
                "创建时间":created_at.strftime('%Y-%m-%d %H:%M:%S'),
            }
            export_datas.append(export_data)
        return export_datas

    def export_to_excel(self,export_datas,filename):
        df = pd.DataFrame(export_datas)
        df.to_excel(f"{filename}.xlsx", index=False)
        print(f"✅ 导出成功: {filename}.xlsx")

    def run(self,url,params,filename):
        try:
            data = self.get_goods_list(url,params)
            file_path = self.save_response(filename,data)
            return file_path
        except Exception as e:
            print(f"❌ 运行失败: {e}")
            return None

if __name__ == "__main__":
    cookie = "P_INFO=m17350754926@163.com|1752741552|1|mail163|00&99|null&null&null#CN&null#10#0#0|173926&1||17350754926@163.com; NTES_CMT_USER_INFO=1225918163%7C%E6%9C%89%E6%80%81%E5%BA%A6%E7%BD%91%E5%8F%8B194wrj%7Chttp%3A%2F%2Fcms-bucket.nosdn.127.net%2F2018%2F08%2F13%2F078ea9f65d954410b62a52ac773875a1.jpeg%7Cfalse%7CbTE3MzUwNzU0OTI2QDE2My5jb20%3D; Device-Id=6D97HE58m81Y58rpbFp0; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=d77zfhI0b5316c98793ec292d93d8a49fdb3; remember_me=U1078483952|l5FYrls1WrkhAIijXLxr7WKO7DcEmr2Q; session=1-zww366jyDaiPSeVHbXQUagkt-wXfl3CiUEEhyitunD_e2022098088; csrf_token=ImExNWUyZmIzYjZmN2M3MDZmNjdjM2IxMjY4OWQxOWNhOWY3Y2EyYmUi.aUJtqA.U6k9iqiJgiEElq34UWIcGRG02EE"
    url = "/api/market/goods/sell_order"
    params = {
        "game": "csgo",
        "goods_id":1116002,
        "page_num":1,
        "sort_by":"price",
        "allow_tradable_cooldown":1,
        "-":time.time()
    }
    now = datetime.datetime.now().strftime('%Y-%m-%d_%H_%M_%S')
    filename = f"buff_马珀丽_{now}"
    spider = BuffGoodsSpider(cookie)
    file_path = spider.run(url,params,filename)
    export_datas = spider.parse_response_data(file_path)
    spider.export_to_excel(export_datas,filename)