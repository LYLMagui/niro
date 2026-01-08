import random
from typing import Optional, Dict

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
            "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
            "referer": referer,
            "sec-ch-ua": self.sec_ch_ua,
            "sec-ch-ua-mobile": "?0",
            "sec-ch-ua-platform": f'"{self.platform}"',
            "sec-fetch-dest": "empty",
            "sec-fetch-mode": "cors",
            "sec-fetch-site": "same-origin",
            "x-requested-with": "XMLHttpRequest",
        }
        return headers

    def update_cookie(self, new_cookie: str):
        """
        更新 Profile 中的 Cookie
        """
        self.cookie = new_cookie


class BrowserHelper:
    """
    浏览器工具类，负责指纹管理和 Profile 生成
    """
    # 预设的指纹库
    FINGERPRINTS = [
        {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "sec_ch_ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
            "platform": "Windows"
        },
        {
            "user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "sec_ch_ua": '"Chromium";v="130", "Google Chrome";v="130", "Not?A_Brand";v="99"',
            "platform": "macOS"
        },
        {
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
            "sec_ch_ua": '"Microsoft Edge";v="129", "Not=A?Brand";v="8", "Chromium";v="129"',
            "platform": "Windows"
        },
        {
            "user_agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "sec_ch_ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
            "platform": "Linux"
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
