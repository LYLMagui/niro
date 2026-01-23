import random
from typing import Optional, Dict
import httpx

class BrowserProfile:
    """
    浏览器身份档案，承载单次任务的指纹和身份信息
    """
    def __init__(self, user_agent: str, sec_ch_ua: str, platform: str, cookie: Optional[str] = None):
        self.user_agent = user_agent
        self.sec_ch_ua = sec_ch_ua
        self.platform = platform
        self.cookie = cookie

    def get_headers(self, referer: str = "https://buff.163.com/market/csgo") -> Dict[str, str]:
        """
        生成完整的请求头
        """
        headers = {
            "User-Agent": self.user_agent,
            "cookie": self.cookie or "",
            "accept": "application/json, text/javascript, */*; q=0.01",
            "accept-language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            "referer": referer,
            "sec-ch-ua": self.sec_ch_ua,
            "sec-ch-ua-mobile": "?0",
            "sec-ch-ua-platform": f'"{self.platform}"',
            "sec-fetch-dest": "empty",
            "sec-fetch-mode": "cors",
            "sec-fetch-site": "same-origin",
            "priority": "u=1, i",
            "x-requested-with": "XMLHttpRequest",
        }
        return headers

    def update_cookies(self, new_cookies: httpx.Cookies):
        """
        同步更新 Cookie：合并新下发的 Cookie 字段，保留旧字段
        注意：如果有同名 Key，新值会覆盖旧值 (Last-Write-Wins)
        """
        # 1. 将现有 Cookie 字符串解析为字典
        current_cookies = {}
        if self.cookie:
            for item in self.cookie.split(';'):
                if '=' in item:
                    key, value = item.strip().split('=', 1)
                    current_cookies[key] = value
        
        # 2. 将 httpx.Cookies (Set-Cookie) 合并进来
        # httpx.Cookies 行为类似于字典，直接迭代即可获取所有新键值对
        for key, value in new_cookies.items():
            current_cookies[key] = value
            
        # 3. 序列化回字符串
        self.cookie = "; ".join([f"{k}={v}" for k, v in current_cookies.items()])

    def update_cookie(self, new_cookie: str):
        """
        更新 Profile 中的 Cookie
        """
        self.cookie = new_cookie


class BrowserHelper:
    """
    浏览器工具类，负责指纹管理和 Profile 生成
    """
    # 预设的指纹库 (对齐用户提供的 143 版本最新抓包数据)
    FINGERPRINTS = [
        {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0",
            "sec_ch_ua": '"Microsoft Edge";v="143", "Chromium";v="143", "Not A(Brand";v="24"',
            "platform": "Windows"
        },
        {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            "sec_ch_ua": '"Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"',
            "platform": "Windows"
        }
    ]

    @classmethod
    def create_profile(cls, user_id: Optional[str] = None, cookie: Optional[str] = None) -> BrowserProfile:
        """
        随机创建一个 BrowserProfile 并自动装填最新的 Cookie
        :param user_id: 用户 ID
        :param cookie: 如果提供了 Cookie 则直接使用，否则从数据库获取
        :return: BrowserProfile 实例
        """
        # 1. 随机选择一个指纹
        fp = random.choice(cls.FINGERPRINTS)
        
        # 2. 如果没有提供 Cookie，则获取该用户的最新 Cookie
        if cookie is None:
            from utils.cookie_util import get_latest_cookie
            cookie = get_latest_cookie(user_id)
        
        return BrowserProfile(
            user_agent=fp["user_agent"],
            sec_ch_ua=fp["sec_ch_ua"],
            platform=fp["platform"],
            cookie=cookie
        )
