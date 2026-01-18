import os
import sys

# 确保能导入 storage
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from storage.database import Session
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
        
    from utils.browser_helper import BrowserHelper
    profile = BrowserHelper.create_profile(cookie=cookie)
    
    url = "https://buff.163.com/api/market/goods?game=csgo&page_num=1&page_size=2"
    headers = profile.get_headers()
    
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
    从数据库获取指定用户的 Buff Cookie (已废弃，建议使用 TaskMessage 中的账号上下文)
    :param user_id: 用户 ID
    :return: Cookie 字符串
    """
    if not user_id:
        return None
        
    session = Session()
    try:
        from storage.models import BuffAccount
        account = session.query(BuffAccount).filter(BuffAccount.user_id == user_id).first()
        if account:
            return account.buff_cookie
    except Exception as e:
        logger.error(f"❌ 获取数据库账号 Cookie 失败: {e}")
    finally:
        Session.remove()
    
    return None
