import requests
import time
from config import settings
from utils.logger import get_logger
from storage.postgres_pool import pg_pool

logger = get_logger(__name__)

class Notifier:
    def __init__(self):
        self.access_token = None
        self.token_expiry = 0

    def _get_access_token(self, corpid, corpsecret):
        """获取企业微信 access_token"""
        # 如果 token 还在有效期内，直接返回
        if self.access_token and time.time() < self.token_expiry:
            return self.access_token

        url = f"https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpid}&corpsecret={corpsecret}"
        try:
            resp = requests.get(url, timeout=10)
            data = resp.json()
            if data.get("errcode") == 0:
                self.access_token = data.get("access_token")
                # token 有效期一般为 7200 秒，提前 5 分钟刷新
                self.token_expiry = time.time() + data.get("expires_in", 7200) - 300
                return self.access_token
            else:
                logger.error(f"获取 WeCom access_token 失败: {data}")
        except Exception as e:
            logger.error(f"获取 WeCom access_token 异常: {e}")
        return None

    def get_user_config(self, user_id):
        """从数据库获取用户的通知配置"""
        if not user_id:
            return None
        
        try:
            sql = "SELECT wecom_corpid, wecom_corpsecret, wecom_agentid, wecom_touser FROM user_buff_settings WHERE user_id = %s"
            res = pg_pool.fetch_one(sql, (user_id,))
            if res and res.get('wecom_corpid') and res.get('wecom_corpsecret'):
                return res
        except Exception as e:
            logger.error(f"查询用户 {user_id} 通知配置失败: {e}")
        return None

    def send_textcard(self, title, description, url, btntxt="详情", user_id=None):
        """
        发送卡片消息
        :param title: 标题
        :param description: 内容描述
        :param url: 点击跳转链接
        :param btntxt: 按钮文字
        :param user_id: 用户 ID，用于获取隔离的通知配置
        """
        # 1. 获取配置 (优先用户配置，其次全局配置)
        config = self.get_user_config(user_id)
        
        corpid = config.get('wecom_corpid') if config else settings.WECOM_CORPID
        corpsecret = config.get('wecom_corpsecret') if config else settings.WECOM_CORPSECRET
        agentid = config.get('wecom_agentid') if config else settings.WECOM_AGENTID
        touser = config.get('wecom_touser') if config else settings.WECOM_TOUSER

        if not corpid or not corpsecret or not agentid:
            logger.warning(f"⚠️ 未配置企业微信参数，跳过通知发送 (UserID: {user_id})")
            return False

        # 2. 获取 token
        token = self._get_access_token(corpid, corpsecret)
        if not token:
            return False

        # 3. 发送消息
        send_url = f"https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}"
        payload = {
            "touser": touser,
            "msgtype": "textcard",
            "agentid": agentid,
            "textcard": {
                "title": title,
                "description": description,
                "url": url,
                "btntxt": btntxt
            },
            "safe": 0,
            "enable_id_trans": 0,
            "enable_duplicate_check": 0
        }

        try:
            resp = requests.post(send_url, json=payload, timeout=10)
            data = resp.json()
            if data.get("errcode") == 0:
                logger.info(f"🚀 通知发送成功 (UserID: {user_id})")
                return True
            else:
                logger.error(f"通知发送失败: {data} (UserID: {user_id})")
        except Exception as e:
            logger.error(f"通知发送异常: {e} (UserID: {user_id})")
        
        return False

# 单例
notifier = Notifier()
