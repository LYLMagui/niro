import os
import sys

# 确保能导入 storage
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from storage.database import Session
from storage.models import UserBuffSettings
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

import requests
import time
import random

def verify_cookie(cookie):
    """
    验证 Cookie 是否有效
    :param cookie: Cookie 字符串
    :return: (is_valid, message)
    """
    if not cookie:
        return False, "Cookie 为空"
        
    url = "https://buff.163.com/api/market/goods?game=csgo&page_num=1&page_size=2"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "cookie": cookie,
        "accept": "application/json, text/javascript, */*; q=0.01",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
        "referer": "https://buff.163.com/market/csgo",
        "sec-ch-ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Windows"',
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
        "x-requested-with": "XMLHttpRequest"
    }
    
    # 获取代理
    from utils.proxy_helper import get_proxies
    proxies = get_proxies()
    
    try:
        response = requests.get(url, headers=headers, proxies=proxies, timeout=10)
        if response.status_code == 403:
            return False, "HTTP 403 Forbidden (IP Blocked or Cookie Expired)"
        
        response.raise_for_status()
        json_data = response.json()
        
        if json_data.get("code") == "OK":
            return True, "Valid"
        elif json_data.get("code") == "Login Required":
            return False, "Login Required (Cookie Expired)"
        else:
            return False, f"API Error: {json_data.get('code')}"
            
    except Exception as e:
        return False, f"Request Error: {str(e)}"

def get_latest_cookie(user_id=None):
    """
    从数据库获取指定用户或最新的 Buff Cookie
    :param user_id: 用户 ID，如果不指定则获取最新的一条
    :return: Cookie 字符串，如果数据库没有则返回 settings 中的默认值
    """
    session = Session()
    try:
        query = session.query(UserBuffSettings)
        if user_id:
            setting = query.filter(UserBuffSettings.user_id == user_id).first()
        else:
            setting = query.order_by(UserBuffSettings.update_time.desc()).first()
            
        if setting and setting.buff_cookie:
            return setting.buff_cookie
    except Exception as e:
        logger.error(f"❌ 获取数据库 Cookie 失败: {e}")
    finally:
        Session.remove()
    
    # 兜底使用配置文件的 Cookie
    return settings.BUFF_COOKIE
