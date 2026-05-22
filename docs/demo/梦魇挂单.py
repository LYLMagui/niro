import sys
import time
import re
from pathlib import Path

import requests

ITEM_OPTIONS = {
    "1": ("梦魇武器箱", "Dreams & Nightmares Case"),
    "2": ("封装的毁灭之手终端机", "Sealed Dead Hand Terminal"),
}
DEFAULT_TOP_N = 50
MIN_QUERY_INTERVAL_SECONDS = 5
COOKIE_FILE_PATH = Path(r"C:\Users\24160\.claude\projects\D--MySpace-niro\memory\steam_cookie.txt")
LAST_QUERY_AT = 0.0


def print_sell_orders(orders, top_n, currency_label):
    print(f"=== 在售挂单分布（卖方前 {top_n} 档）===")
    previous_cumulative = 0
    for index, order in enumerate(orders[:top_n], start=1):
        price = order[0]
        cumulative_qty = order[1]
        exact_qty = cumulative_qty - previous_cumulative
        print(f"第{index:02d}档 | 价格：{currency_label} {price:.2f} | 独立数量：{exact_qty} 件")
        previous_cumulative = cumulative_qty


def parse_ssr_sell_orders(page_text):
    symbol_match = re.search(r'strSymbol\\\":\\\"(.*?)\\\"', page_text)
    whole_units_match = re.search(r'bWholeUnitsOnly\\\":(true|false)', page_text)
    currency_code_match = re.search(r'eCurrency\\\":(\d+)', page_text)
    sell_orders_match = re.search(r'rgCompactSellOrders\\\\\\\":\[(.*?)\]', page_text)
    if not sell_orders_match:
        return None

    symbol = symbol_match.group(1) if symbol_match else ""
    whole_units_only = bool(whole_units_match and whole_units_match.group(1) == "true")
    currency_code = int(currency_code_match.group(1)) if currency_code_match else None
    currency_name_map = {
        1: "USD",
        3: "EUR",
        8: "JPY",
        23: "CNY",
    }
    currency_name = currency_name_map.get(currency_code, f"UNKNOWN-{currency_code}" if currency_code is not None else "UNKNOWN")

    raw_numbers = sell_orders_match.group(1)
    values = [int(value.strip()) for value in raw_numbers.split(",") if value.strip()]
    if len(values) < 2:
        return []

    orders = []
    cumulative = 0
    for index in range(0, len(values), 2):
        raw_price = values[index]
        quantity = values[index + 1]
        cumulative += quantity
        price_value = raw_price / 100
        if whole_units_only:
            price_value = float(f"{price_value:.2f}".rstrip("0").rstrip("."))
        orders.append([price_value, cumulative])

    print(f"当前页面币种上下文：{currency_name} | 页面符号：{symbol or '(empty)'} | 整数货币：{whole_units_only}")
    if currency_code == 8:
        print("注意：当前匿名页面返回的是 JPY 价格桶，不等于你登录后网页里看到的本地币种价格。")
    return orders


def apply_rate_limit():
    global LAST_QUERY_AT
    now = time.time()
    elapsed = now - LAST_QUERY_AT
    if LAST_QUERY_AT > 0 and elapsed < MIN_QUERY_INTERVAL_SECONDS:
        wait_seconds = MIN_QUERY_INTERVAL_SECONDS - elapsed
        print(f"请求过快，请等待 {wait_seconds:.1f} 秒后再查询。")
        time.sleep(wait_seconds)
    LAST_QUERY_AT = time.time()


def read_saved_cookie():
    if not COOKIE_FILE_PATH.exists():
        return ""
    cookie = COOKIE_FILE_PATH.read_text(encoding="utf-8").strip()
    if cookie:
        print(f"已读取本地 Cookie 文件：{COOKIE_FILE_PATH}")
    return cookie


def save_cookie(cookie):
    COOKIE_FILE_PATH.write_text(cookie.strip(), encoding="utf-8")
    print(f"Cookie 已保存到本地文件：{COOKIE_FILE_PATH}")


def prompt_cookie(saved_cookie):
    if saved_cookie:
        choice = input("检测到已保存的 Cookie。直接使用请输入 y，重新输入请输入 n：").strip().lower()
        if choice in ("", "y", "yes"):
            return saved_cookie

    cookie = input("请粘贴 Steam Cookie，然后按回车：").strip()
    if not cookie:
        print("未输入 Cookie，程序结束。")
        return ""
    save_cookie(cookie)
    return cookie


def get_price_histogram(market_hash_name, steam_cookie="", top_n=DEFAULT_TOP_N):
    apply_rate_limit()

    proxies = {
        "http": "http://127.0.0.1:7897",
        "https": "http://127.0.0.1:7897",
    }

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    if steam_cookie.strip():
        headers["Cookie"] = steam_cookie.strip()

    url = f"https://steamcommunity.com/market/listings/730/{market_hash_name}"
    print(f"正在获取 {market_hash_name} 的底层数据...")
    if steam_cookie.strip():
        print("已携带 Steam Cookie 发起请求。")
    else:
        print("当前未携带 Steam Cookie，返回的可能是匿名价格桶。")

    try:
        response = requests.get(url, headers=headers, proxies=proxies, timeout=10)
        response.raise_for_status()
    except Exception as e:
        print(f"网络请求失败: {e}")
        return

    page_text = response.text
    final_url = response.url
    print(f"最终落地页面: {final_url}")

    if steam_cookie.strip():
        item_nameid_match = re.search(r'Market_LoadOrderSpread\(\s*(\d+)\s*\)', page_text)
        wallet_currency_match = re.search(r'"wallet_currency":(\d+)', page_text)
        if not item_nameid_match:
            print("已携带 Cookie，但未找到旧版页面里的 item_nameid。")
            return

        item_nameid = item_nameid_match.group(1)
        wallet_currency = int(wallet_currency_match.group(1)) if wallet_currency_match else 23
        print(f"成功锁定 item_nameid: {item_nameid} | wallet_currency={wallet_currency}")

        time.sleep(2)

        histogram_url = "https://steamcommunity.com/market/itemordershistogram"
        params = {
            "country": "CN",
            "language": "schinese",
            "currency": wallet_currency,
            "item_nameid": item_nameid,
            "two_factor": 0,
        }

        try:
            hist_resp = requests.get(histogram_url, params=params, headers=headers, proxies=proxies, timeout=10)
            hist_resp.raise_for_status()
            data = hist_resp.json()
        except Exception as e:
            print(f"挂单深度接口请求失败: {e}")
            return

        if data.get("success") != 1:
            print(f"挂单深度接口返回异常: {data}")
            return

        sell_orders = data.get("sell_order_graph", [])
        if not sell_orders:
            print("挂单深度接口返回成功，但没有卖盘数据。")
            return

        print("正在解析旧版挂单深度接口返回的在售价格列表...\n")
        print_sell_orders(sell_orders, top_n, "CNY")
        return

    sell_orders = parse_ssr_sell_orders(page_text)
    if sell_orders is None:
        print("未找到新版页面内嵌的卖盘数据，请检查代理网络、Cookie 或页面结构是否再次变更。")
        return
    if not sell_orders:
        print("卖盘数据为空或格式异常。")
        return

    print("正在解析新版页面内嵌的在售价格列表...\n")
    print_sell_orders(sell_orders, top_n, "SSR")


def select_item(choice):
    item_option = ITEM_OPTIONS.get(choice)
    if not item_option:
        return None
    return item_option[1]


def run_interactive_loop():
    saved_cookie = read_saved_cookie()
    steam_cookie = prompt_cookie(saved_cookie)
    if not steam_cookie:
        return

    while True:
        print("请选择要查询的饰品：")
        for option, item in ITEM_OPTIONS.items():
            print(f"{option}. {item[0]} ({item[1]})")
        print("c. 重新输入并保存 Cookie")
        print("q. 退出程序")

        choice = input("请输入选项编号：").strip().lower()
        if choice == "q":
            print("程序已退出。")
            return
        if choice == "c":
            steam_cookie = prompt_cookie("")
            if not steam_cookie:
                return
            continue

        market_hash_name = select_item(choice)
        if not market_hash_name:
            print("无效选项，请重新输入。")
            continue

        get_price_histogram(market_hash_name, steam_cookie=steam_cookie, top_n=DEFAULT_TOP_N)
        next_action = input("查询完成。按回车继续查询，输入 q 退出：").strip().lower()
        if next_action == "q":
            print("程序已退出。")
            return


def main():
    if len(sys.argv) >= 3:
        market_hash_name = select_item(sys.argv[1])
        steam_cookie = sys.argv[2]
        if not market_hash_name:
            print("无效的饰品选项，仅支持 1 或 2。")
            return 1
        get_price_histogram(market_hash_name, steam_cookie=steam_cookie, top_n=DEFAULT_TOP_N)
        return 0

    run_interactive_loop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
