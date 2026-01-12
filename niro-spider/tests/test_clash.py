import sys
import os
import time

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

from utils.proxy_helper import refresh_proxies, _switch_clash_node
from utils.logger import setup_logging, get_current_ip_cached
from config import settings

def test_clash_integration():
    """测试 Clash 切换逻辑"""
    setup_logging()
    
    # 强制启用代理进行测试
    settings.ENABLE_PROXY = True
    
    print("\n" + "="*50)
    print("🚀 开始测试 Clash 代理切换功能")
    print("="*50)

    # 1. 打印当前配置
    print(f"📍 代理地址 (PROXY_URL): {settings.PROXY_URL}")
    print(f"📍 是否启用代理: {settings.ENABLE_PROXY}")
    print(f"📍 Clash API 地址: {settings.CLASH_API_URL}")
    print(f"📍 Clash 代理组: {settings.CLASH_GROUP_NAME}")
    
    # 2. 获取切换前的 IP
    print("\n🔍 正在检测当前出口 IP...")
    old_ip = get_current_ip_cached(force_refresh=True)
    print(f"✅ 切换前 IP: {old_ip}")

    # 3. 触发切换逻辑
    print("\n🔄 正在触发 Clash 节点切换...")
    # 直接调用私有方法看详细过程
    success = _switch_clash_node()
    
    if success:
        print("✨ Clash API 调用成功，正在等待网络生效 (5s)...")
        time.sleep(5)
        
        # 4. 获取切换后的 IP
        print("\n🔍 正在重新检测出口 IP...")
        new_ip = get_current_ip_cached(force_refresh=True)
        print(f"✅ 切换后 IP: {new_ip}")
        
        if old_ip != new_ip:
            print(f"\n🎉 测试通过！出口 IP 已成功变更: {old_ip} -> {new_ip}")
        else:
            print(f"\n⚠️ 警告: Clash 报告切换成功，但出口 IP 未发生变化。可能原因：")
            print("1. 选中的新节点与旧节点出口 IP 相同")
            print("2. Clash 规则导致检测请求未走代理")
            print("3. 网络层存在缓存")
    else:
        print("\n❌ Clash API 调用失败，请检查配置或服务器端口开放情况。")

    print("="*50 + "\n")

if __name__ == "__main__":
    test_clash_integration()
