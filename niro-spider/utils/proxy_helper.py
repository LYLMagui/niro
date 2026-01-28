import random
import requests
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

def _switch_clash_node():
    """
    通过 Clash 外部控制 API 切换节点
    """
    api_url = getattr(settings, 'CLASH_API_URL', 'http://127.0.0.1:9090')
    api_secret = getattr(settings, 'CLASH_API_SECRET', '')
    group_name = getattr(settings, 'CLASH_GROUP_NAME', 'Proxies')

    headers = {}
    if api_secret:
        headers["Authorization"] = f"Bearer {api_secret}"

    # 切换节点时不走代理，直接连接 Clash 控制端口
    direct_session = requests.Session()
    direct_session.trust_env = False 

    try:
        # 1. 获取所有代理和组信息
        resp = direct_session.get(f"{api_url}/proxies", headers=headers, timeout=5)
        if resp.status_code != 200:
            logger.warning(f"⚠️ 无法获取 Clash 代理信息: {resp.status_code} (请检查 {api_url} 是否可达)")
            return False

        data = resp.json()
        proxies_data = data.get("proxies", {})
        
        # 2. 查找指定的代理组
        group = proxies_data.get(group_name)
        if not group or group.get("type") not in ["Selector", "URLTest", "Fallback", "LoadBalance"]:
            # 如果找不到指定的组，尝试列出所有选择器组供排查
            selectors = [k for k, v in proxies_data.items() if v.get("type") == "Selector"]
            logger.warning(f"⚠️ Clash 代理组 '{group_name}' 不存在或不是选择器类型。可用组: {selectors}")
            return False

        # 3. 获取组内所有节点并随机选一个不同的
        all_nodes = group.get("all", [])
        current_node = group.get("now", "")
        
        # 排除掉当前节点和一些特殊节点（如 DIRECT, REJECT）
        # 增加排除 [Auto] 等自动测速组，确保切换到的是具体的节点
        exclude_keywords = [current_node, "DIRECT", "REJECT", "GLOBAL", "Auto", "自动", "负载均衡"]
        available_nodes = [n for n in all_nodes if not any(k in n for k in exclude_keywords)]
        
        if not available_nodes:
            logger.warning(f"⚠️ Clash 代理组 '{group_name}' 中没有其他可用节点")
            return False

        new_node = random.choice(available_nodes)
        
        # 4. 执行切换
        switch_resp = direct_session.put(
            f"{api_url}/proxies/{group_name}", 
            headers=headers, 
            json={"name": new_node},
            timeout=5
        )
        
        if switch_resp.status_code == 204:
            logger.info(f"🔄 Clash 节点已从 [{current_node}] 切换至 [{new_node}]")
            return True
        else:
            logger.warning(f"❌ Clash 节点切换失败: {switch_resp.status_code} {switch_resp.text}")
            return False

    except Exception as e:
        logger.error(f"❌ 调用 Clash API 出错: {e}")
        return False

def get_proxies():
    """
    获取代理配置
    根据 ENABLE_PROXY 开关决定是否启用代理
    优先使用 PROXY_URL (v2rayA 等单点代理)
    其次使用 PROXIES (代理池列表)
    :return: 代理字典 { 'http': '...', 'https': '...' } 或 None
    """
    # 0. 检查代理开关
    enable_proxy = getattr(settings, 'ENABLE_PROXY', False)
    if not enable_proxy:
        return None

    # 1. 优先使用全局单点代理
    proxy_url = getattr(settings, 'PROXY_URL', None)
    if proxy_url:
        # 自动补全协议头 (httpx 必须要求)
        if "://" not in proxy_url:
            proxy_url = f"http://{proxy_url}"
            
        proxies = {
            "http": proxy_url,
            "https": proxy_url
        }
        # logger.debug(f"🌐 使用全局代理: {proxy_url}")
        return proxies

    # 2. 备选使用代理池
    if hasattr(settings, 'PROXIES') and settings.PROXIES:
        proxy_ip = random.choice(settings.PROXIES)
        proxies = {
            "http": f"http://{proxy_ip}",
            "https": f"http://{proxy_ip}"
        }
        # logger.debug(f"🌐 使用代理池: {proxy_ip}")
        return proxies
    
    return None

def refresh_proxies():
    """
    强制刷新代理状态
    1. 如果是代理池，下一次 get_proxies() 自然会随机换一个
    2. 如果是单点代理 (如 Clash)，尝试通过 API 切换节点
    """
    # 针对单点代理 (Clash) 的主动切换逻辑
    proxy_url = getattr(settings, 'PROXY_URL', None)
    if proxy_url:
        # 如果配置了 Clash API，尝试切换节点
        _switch_clash_node()

    # 强制刷新出口 IP 缓存，确保下次请求能重新检测
    from utils.logger import get_current_ip_cached
    old_ip = get_current_ip_cached()
    new_ip = get_current_ip_cached(force_refresh=True)
    
    if old_ip != new_ip:
        logger.info(f"🔄 出口 IP 已变更: {old_ip} -> {new_ip}")
    else:
        logger.warning(f"⚠️ 出口 IP 未发生变化: {new_ip}")
    
    return new_ip

def get_random_proxy():
    """兼容旧函数名"""
    return get_proxies()
