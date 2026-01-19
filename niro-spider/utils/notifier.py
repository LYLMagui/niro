import requests
import time
from config import settings
from utils.logger import get_logger
logger = get_logger(__name__)

class Notifier:
    def __init__(self):
        # 存储不同 corpid + corpsecret 的 token 信息
        # key: (corpid, corpsecret), value: {"token": xxx, "expiry": xxx}
        self.tokens = {}

    def _get_access_token(self, corpid, corpsecret):
        """获取企业微信 access_token"""
        key = (corpid, corpsecret)
        token_info = self.tokens.get(key)
        
        # 如果 token 还在有效期内，直接返回
        if token_info and time.time() < token_info["expiry"]:
            return token_info["token"]

        url = f"https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpid}&corpsecret={corpsecret}"
        try:
            resp = requests.get(url, timeout=10)
            data = resp.json()
            if data.get("errcode") == 0:
                access_token = data.get("access_token")
                # token 有效期一般为 7200 秒，提前 5 分钟刷新
                expiry = time.time() + data.get("expires_in", 7200) - 300
                self.tokens[key] = {"token": access_token, "expiry": expiry}
                return access_token
            else:
                logger.error(f"获取 WeCom access_token 失败: {data}")
        except Exception as e:
            logger.error(f"获取 WeCom access_token 异常: {e}")
        return None

    def _get_config(self, user_id=None):
        """获取配置 (预留，目前统一使用 settings 中的全局配置)"""
        # 如果将来需要支持多用户不同机器人配置，可在此处扩展（例如从 Redis 获取）
        return None

    def send_text(self, content, user_id=None):
        """
        发送纯文本消息 (兼容性最强，无跳转)
        :param content: 文本内容
        :param user_id: 用户 ID
        """
        config = self._get_config(user_id)
        corpid = config.get('wecom_corpid') if config else settings.WECOM_CORPID
        corpsecret = config.get('wecom_corpsecret') if config else settings.WECOM_CORPSECRET
        agentid = config.get('wecom_agentid') if config else settings.WECOM_AGENTID
        touser = config.get('wecom_touser') if config else settings.WECOM_TOUSER

        if not corpid or not corpsecret or not agentid:
            logger.warning(f"⚠️ 未配置企业微信参数，跳过通知发送 (UserID: {user_id})")
            return False

        token = self._get_access_token(corpid, corpsecret)
        if not token: return False

        send_url = f"https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}"
        payload = {
            "touser": touser,
            "msgtype": "text",
            "agentid": agentid,
            "text": {"content": content}
        }

        try:
            resp = requests.post(send_url, json=payload, timeout=10)
            data = resp.json()
            if data.get("errcode") == 0:
                logger.info(f"🚀 文本通知发送成功 (UserID: {user_id})")
                return True
            else:
                logger.error(f"❌ 文本通知发送失败: {data} (UserID: {user_id})")
                return False
        except Exception as e:
            logger.error(f"发送纯文本通知异常: {e}")
            return False

    def send_markdown(self, content, user_id=None):
        """
        发送 Markdown 消息 (无强制跳转)
        :param content: Markdown 内容
        :param user_id: 用户 ID
        """
        config = self._get_config(user_id)
        corpid = config.get('wecom_corpid') if config else settings.WECOM_CORPID
        corpsecret = config.get('wecom_corpsecret') if config else settings.WECOM_CORPSECRET
        agentid = config.get('wecom_agentid') if config else settings.WECOM_AGENTID
        touser = config.get('wecom_touser') if config else settings.WECOM_TOUSER

        if not corpid or not corpsecret or not agentid:
            logger.warning(f"⚠️ 未配置企业微信参数，跳过通知发送 (UserID: {user_id})")
            return False

        token = self._get_access_token(corpid, corpsecret)
        if not token: return False

        send_url = f"https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}"
        payload = {
            "touser": touser,
            "msgtype": "markdown",
            "agentid": agentid,
            "markdown": {"content": content}
        }

        try:
            resp = requests.post(send_url, json=payload, timeout=10)
            data = resp.json()
            if data.get("errcode") == 0:
                logger.info(f"🚀 Markdown通知发送成功 (UserID: {user_id})")
                return True
            else:
                logger.error(f"❌ Markdown通知发送失败: {data} (UserID: {user_id})")
                return False
        except Exception as e:
            logger.error(f"发送 Markdown 通知异常: {e}")
            return False

    def send_textcard(self, title, description, url="http://localhost", btntxt="", user_id=None):
        """
        发送卡片消息 (url 设为 localhost 以减少跳转干扰)
        :param title: 标题
        :param description: 内容描述
        :param url: 点击跳转链接 (强制要求，默认为 localhost)
        :param btntxt: 按钮文字 (设为空则不显示底部按钮)
        :param user_id: 用户 ID
        """
        # 1. 获取配置 (优先用户配置，其次全局配置)
        config = self._get_config(user_id)
        
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
                "url": url or "http://localhost"
            },
            "safe": 0,
            "enable_id_trans": 0,
            "enable_duplicate_check": 0
        }
        
        # 只有当 btntxt 不为空时才加入字段
        if btntxt:
            payload["textcard"]["btntxt"] = btntxt

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
