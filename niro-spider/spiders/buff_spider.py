from http.cookies import SimpleCookie
import json
import os

import pendulum
import pydash
import requests
from tenacity import (
    before_sleep_log,
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_random_exponential,
)

from config import settings
from dto.buff_dto import BuffSellOrderResponse, ParsedBuffItemDTO
from enums.buff_enums import BuffGameType, BuffPaymentMethod, BuffSortStrategy
from utils.browser_helper import BrowserHelper
from utils.exception_handler import LoginRequiredError, handle_api_error
from utils.logger import get_logger
from utils.network_util import log_request_ip
from utils.proxy_helper import get_proxies, refresh_proxies

logger = get_logger(__name__)


def before_retry_callback(retry_state):
    """重试前的回调：处理代理失效与节点切换"""
    attempt = retry_state.attempt_number
    exception = retry_state.outcome.exception()
    
    # 记录错误原因
    error_msg = str(exception)
    if "Read timed out" in error_msg:
        logger.warning(f"⏳ [超时重试] Buff 接口请求超时，正在尝试切换代理节点... ({attempt}/3)")
    elif "429" in error_msg:
        logger.warning(f"🚫 [限流重试] 触发 Buff 频率限制 (429)，正在更换 IP 规避... ({attempt}/3)")
    else:
        logger.warning(f"🔄 [异常重试] 请求出现异常: {error_msg}，准备重试... ({attempt}/3)")

    try:
        refresh_proxies()
    except Exception as e:
        logger.error(f"❌ 尝试切换代理节点失败: {e}")

class BuffSpider:
    def __init__(self, user_id=None):
        self.host = "https://buff.163.com"
        self.user_id = user_id
        # 绑定用户上下文，方便日志追踪
        self.logger = logger.bind(user_id=user_id)
# 1. 代理配置
        self.proxies = get_proxies()
        if self.proxies:
            # 强制刷新日志系统中的出口IP缓存，确保后续日志显示的是代理IP
            from utils.logger import get_current_ip_cached
            new_ip = get_current_ip_cached(force_refresh=True)
            self.logger.info(f"🛰️ BuffSpider 已启用代理: {self.proxies.get('http')} | 当前出口IP: {new_ip}")
        else:
            self.logger.info("🌐 BuffSpider 未启用代理，将使用本地网络")
        
        # 初始化时随机生成一个浏览器指纹并绑定 Cookie
        self.profile = BrowserHelper.create_profile(self.user_id)
        self.logger.info(f"🎭 已为当前任务分配指纹: {self.profile.user_agent}")

    def refresh_cookie(self):
        """从数据库刷新指定用户的有效 Cookie"""
        from utils.cookie_util import get_latest_cookie
        new_cookie = get_latest_cookie(self.user_id)
        if new_cookie != self.profile.cookie:
            self.profile.update_cookie(new_cookie)
            self.logger.info(f"🔄 [Cookie] 已为用户 {self.user_id if self.user_id else 'Global'} 加载最新 Cookie")
        elif not self.profile.cookie:
            self.logger.warning(f"⚠️ 无法加载用户 {self.user_id} 的有效 Cookie")

    def refresh_profile(self):
        """重新生成浏览器指纹 Profile (通常用于任务启动时)"""
        self.profile = BrowserHelper.create_profile(self.user_id)
        self.logger.info(f"🎭 已重新分配浏览器指纹: {self.profile.user_agent}")

    @handle_api_error(default_return=[])
    @retry(
        stop=stop_after_attempt(3),
        # 指数退避 + 随机抖动 (Jitter)
        # base=2, 2^1=2, 2^2=4, 2^3=8... 在此基础上加入随机偏移
        wait=wait_random_exponential(multiplier=1, min=2, max=20),
        retry=retry_if_exception_type(requests.exceptions.RequestException),
        before_sleep=before_retry_callback,
        reraise=True
    )
    def get_goods_list(self, goods_id, page_num=1, min_paintwear=None, max_paintwear=None, max_price=None, sort_by=BuffSortStrategy.PRICE_ASC.value):
        """
        获取饰品列表
        :param goods_id: 饰品ID
        :param page_num: 页码
        :param min_paintwear: 最小磨损
        :param max_paintwear: 最大磨损
        :param max_price: 最大价格
        :param sort_by: 排序方式，默认价格正序
        :return: 解析后的数据列表
        """
        url = "/api/market/goods/sell_order"
        params = {
            "game": BuffGameType.CSGO.value,
            "goods_id": goods_id,
            "page_num": page_num,
            "sort_by": sort_by,
        }

        # 可选过滤参数
        if min_paintwear is not None:
            params["min_paintwear"] = min_paintwear
        if max_paintwear is not None:
            params["max_paintwear"] = max_paintwear
        if max_price is not None:
            params["max_price"] = max_price

        # 每次请求动态获取代理，确保重试时能切换
        proxies = get_proxies()
        
        self.logger.debug(f"🔍 [Request] GET {url} | Params: {params}")
        
        response = requests.get(
            self.host + url, headers=self.profile.get_headers(), params=params, proxies=proxies, timeout=10
        )
        
        # 强制设置编码，防止中文乱码
        response.encoding = 'utf-8'
        response.raise_for_status()
        
        # 使用 Pydantic 解析响应
        try:
            # 优先使用 json() 解析更安全
            resp_json = response.json()
            resp_data = BuffSellOrderResponse.model_validate(resp_json)
            parsed_items = self._parse_data_v2(resp_data)
            
            # 简介打印前五条数据
            if parsed_items:
                top_5 = parsed_items[:5]
                self.logger.info(f"📊 [GoodsList] 已获取 {len(parsed_items)} 条数据，前 5 条：")
                for idx, item in enumerate(top_5, 1):
                    self.logger.info(f"  {idx}. {item['name']} | 价格: {item['price_buff']} | 磨损: {item['paintwear']}")
            
            return parsed_items
        except LoginRequiredError as e:
            raise e
        except Exception as e:
            self.logger.error(f"解析 Buff 响应失败: {e}")
            # 如果 json 解析失败，尝试原始文本
            try:
                resp_data = BuffSellOrderResponse.model_validate_json(response.text)
                parsed_items = self._parse_data_v2(resp_data)
                
                if parsed_items:
                    top_5 = parsed_items[:5]
                    self.logger.info(f"📊 [GoodsList] 已获取 {len(parsed_items)} 条数据，前 5 条：")
                    for idx, item in enumerate(top_5, 1):
                        self.logger.info(f"  {idx}. {item['name']} | 价格: {item['price_buff']} | 磨损: {item['paintwear']}")
                
                return parsed_items
            except LoginRequiredError as e:
                raise e
            except Exception as e2:
                self.logger.error(f"解析 Buff 响应最终失败: {e2}")
                return []

    def _parse_data_v2(self, resp: BuffSellOrderResponse):
        """使用 Pydantic 模型进行解析的改进版"""
        if resp.code == "Login Required":
            self.logger.error("🔑 Cookie 已失效或未登录")
            raise LoginRequiredError("Buff Login Required")
            
        if resp.code != "OK" or not resp.data:
            self.logger.warning(f"响应数据异常: {resp.msg}")
            return []

        data = resp.data
        parsed_items = []
        
        for item in data.items:
            goods_info = data.goods_infos.get(str(item.goods_id), {})
            user_info = data.user_infos.get(str(item.user_id), {})
            created_at_dt = pendulum.from_timestamp(item.created_at)

            parsed_item = ParsedBuffItemDTO(
                id=item.id,
                goods_id=item.goods_id,
                name=goods_info.name if goods_info else "",
                price_usd=goods_info.steam_price if goods_info else "",
                price_cny=goods_info.steam_price_cny if goods_info else "",
                paintwear=item.asset_info.paintwear,
                price_buff=item.price,
                sell_min_price=float(goods_info.sell_min_price) if goods_info else 0.0,
                buy_max_price=float(goods_info.buy_max_price) if goods_info else 0.0,
                sell_num=goods_info.sell_num if goods_info else 0,
                user_id=item.user_id,
                user_nickname=user_info.nickname if user_info else "",
                created_at=created_at_dt.to_datetime_string(),
                crawled_at=pendulum.now().to_datetime_string(),
                rarity=item.asset_info.rarity,
                exterior=item.asset_info.exterior,
                stickers=item.asset_info.stickers,
                supported_pay_methods=item.supported_pay_methods
            )
            parsed_items.append(parsed_item.model_dump())

        return parsed_items

    @handle_api_error(default_return=None)
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_random_exponential(multiplier=1, min=2, max=20),
        retry=retry_if_exception_type(requests.exceptions.RequestException),
        before_sleep=before_retry_callback,
        reraise=True
    )
    def buy(self, goods_id, item_id, price_str, pay_method=BuffPaymentMethod.BALANCE, allow_tradable_cooldown=0):
        """
        下单购买接口
        :param goods_id: 饰品ID (如 33852)
        :param item_id: 商品具体Item ID (如 sell_order_id)
        :param price_str: 购买价格 (字符串)
        :param pay_method: 支付方式 (44=余额/网易支付, 3=支付宝)
        :param allow_tradable_cooldown: 是否允许冷却
        :return: 成功返回订单数据(dict)，失败返回 None
        """
        url = "/api/market/goods/buy"
        # 确保 item_id 是字符串
        sell_order_id = str(item_id)
        
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "sell_order_id": sell_order_id,
            "price": float(price_str),
            "pay_method": int(pay_method),
            "allow_tradable_cooldown": int(allow_tradable_cooldown),
        }
        
        # 补充 Headers
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        csrf_token = self._get_csrf_token()
        headers.update({
            "X-CSRFToken": csrf_token,
            "Content-Type": "application/json",
        })
        
        logger.info(f"🛒 [发起购买] POST {url} | GoodsID={goods_id} | ItemID={item_id} | Price={price_str} | PayMethod={pay_method}")
        
        # 动态获取代理
        proxies = get_proxies()
        log_request_ip(proxies, prefix=f"[BuyOrder] ")
        
        response = requests.post(self.host + url, headers=headers, json=payload, proxies=proxies, timeout=10)
        
        if response.status_code != 200:
            logger.error(f"❌ 下单请求失败, HTTP状态码: {response.status_code}, 内容: {response.text}")
            return None

        res_json = response.json()
        if res_json.get("code") == "OK":
            data = res_json.get("data", {})
            order_id = data.get("id")
            state = data.get("state_text", "未知")
            pay_url = data.get("pay_url")
            logger.info(f"✅ 下单成功! 订单号: {order_id}, 状态: {state}")
            if pay_url:
                logger.info(f"🔗 支付链接: {pay_url}")
            return data
        else:
            # 返回完整的 JSON 结果，以便上层判断错误类型（如：该饰品暂不支持此支付方式）
            error_msg = res_json.get("error") or res_json.get("msg") or "未知错误"
            res_json["error_msg"] = error_msg # 方便统一获取
            logger.error(f"❌ 下单失败: {res_json.get('code')} - {error_msg}")
            return res_json # 返回整个响应用于逻辑判断

    @handle_api_error(default_return=(None, None))
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_random_exponential(multiplier=1, min=2, max=20),
        retry=retry_if_exception_type(requests.exceptions.RequestException),
        before_sleep=before_retry_callback,
        reraise=True
    )
    def batch_buy_preview(self, goods_id, sell_order_ids: list):
        """
        批量下单预览 (收银台第一步)
        :param goods_id: 饰品ID
        :param sell_order_ids: 具体的挂单ID列表
        :return: (preview_data, trace_id)
        """
        url = "/api/market/goods/batch_buy/preview"
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "sell_orders": sell_order_ids,
            "select_epay": 1,
            "steamid": None
        }
        
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        headers.update({
            "X-CSRFToken": self._get_csrf_token(),
            "Content-Type": "application/json",
        })
        
        self.logger.info(f"📋 [批量下单-预览] GoodsID={goods_id} | OrdersCount={len(sell_order_ids)}")
        proxies = get_proxies()
        response = requests.post(self.host + url, headers=headers, json=payload, proxies=proxies, timeout=10)
        response.raise_for_status()
        
        res_json = response.json()
        trace_id = response.headers.get("buff-cashier-trace-id")
        
        if res_json.get("code") == "OK":
            return res_json.get("data"), trace_id
        else:
            self.logger.error(f"❌ 批量下单预览失败: {res_json.get('msg')}")
            return None, trace_id

    @handle_api_error(default_return=None)
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_random_exponential(multiplier=1, min=2, max=20),
        retry=retry_if_exception_type(requests.exceptions.RequestException),
        before_sleep=before_retry_callback,
        reraise=True
    )
    def batch_buy_create(self, goods_id, pay_method, total_price, num=1, trace_id=None):
        """
        创建批量下单订单 (收银台第二步)
        :param goods_id: 饰品ID
        :param pay_method: 支付方式 (44=网易支付/余额)
        :param total_price: 总价 (必须与预览一致)
        :param num: 购买数量
        :return: batch_buy_id
        """
        url = "/api/market/goods/batch_buy/create"
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "pay_method": int(pay_method),
            "frozen_amount": float(total_price),
            "max_price": str(total_price),
            "num": str(num),
            "steamid": None
        }
        
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        headers.update({
            "X-CSRFToken": self._get_csrf_token(),
            "Content-Type": "application/json",
        })
        if trace_id:
            headers["buff-cashier-trace-id"] = trace_id
            
        self.logger.info(f"🏗️ [批量下单-创建] GoodsID={goods_id} | Method={pay_method} | Total={total_price}")
        proxies = get_proxies()
        response = requests.post(self.host + url, headers=headers, json=payload, proxies=proxies, timeout=10)
        response.raise_for_status()
        
        res_json = response.json()
        if res_json.get("code") == "OK":
            return res_json.get("data", {}).get("batch_buy_id")
        else:
            self.logger.error(f"❌ 批量下单创建失败: {res_json.get('msg')}")
            return None

    @handle_api_error(default_return=None)
    def batch_buy_check_state(self, batch_buy_id, goods_id):
        """
        检查批量下单支付状态 (收银台第四步 - 轮询)
        :param batch_buy_id: 批量下单ID
        :param goods_id: 饰品ID
        :return: 状态数据
        """
        url = "/api/market/goods/batch_buy/check_state"
        params = {
            "batch_buy_id": batch_buy_id,
            "_": int(pendulum.now().timestamp() * 1000)
        }
        
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        proxies = get_proxies()
        response = requests.get(self.host + url, headers=headers, params=params, proxies=proxies, timeout=10)
        response.raise_for_status()
        
        return response.json()

    @handle_api_error(default_return=None)
    def batch_buy_pay(self, batch_buy_id, goods_id):
        """
        执行最终支付 (收银台第三步 - 针对余额支付)
        """
        url = "/api/market/goods/batch_buy/epay_page_pay"
        params = {
            "batch_buy_id": batch_buy_id,
            "_": int(pendulum.now().timestamp() * 1000)
        }
        
        headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        
        self.logger.info(f"💰 [批量下单-支付] BatchID={batch_buy_id}")
        proxies = get_proxies()
        response = requests.get(self.host + url, headers=headers, params=params, proxies=proxies, timeout=10)
        response.raise_for_status()
        
        return response.json()

    def buy_v3(self, goods_id, sell_order_ids: list, pay_method=BuffPaymentMethod.BALANCE):
        """
        高度模拟真人的批量下单完整流程 (推荐使用)
        :param goods_id: 饰品ID
        :param sell_order_ids: 订单ID列表 (如果是单件，也请传入列表 [id])
        :param pay_method: 支付方式 (44-余额)
        :return: 最终支付结果
        """
        try:
            # 1. 预览
            preview_data, trace_id = self.batch_buy_preview(goods_id, sell_order_ids)
            if not preview_data: return None
            
            # 模拟官网逻辑：从 preview 响应中找到选中的支付方式的价格
            selected_method = next((m for m in preview_data.get("pay_methods", []) if m.get("value") == int(pay_method)), None)
            if not selected_method:
                self.logger.error(f"❌ 预览失败: 不支持支付方式 {pay_method}")
                return None
            
            total_price = selected_method.get("price_with_pay_fee")
            
            # 模拟真人思考时间
            import time, random
            time.sleep(random.uniform(0.8, 1.5))
            
            # 2. 创建批量订单 (锁定库存)
            batch_buy_id = self.batch_buy_create(goods_id, pay_method, total_price, num=len(sell_order_ids), trace_id=trace_id)
            if not batch_buy_id: return None
            
            time.sleep(random.uniform(0.5, 1.0))
            
            # 3. 支付预处理
            pay_prepare = self.batch_buy_pay(batch_buy_id, goods_id)
            if not pay_prepare or pay_prepare.get("code") != "OK":
                self.logger.error(f"❌ 支付预处理失败: {pay_prepare}")
                return pay_prepare
            
            # 如果 auto_pay 为 false，说明需要跳转到网易支付页面进行“支付授权”
            pay_data = pay_prepare.get("data", {})
            proxies = get_proxies()
            if pay_data.get("auto_pay") is False and "elements" in pay_data:
                epay_url = pay_data["elements"].get("url")
                if epay_url:
                    self.logger.info(f"🔗 [批量下单] 支付链接 (请手动支付): {epay_url}")
                    # 如果是非余额支付，我们需要返回支付链接供用户操作
                    if int(pay_method) != BuffPaymentMethod.BALANCE:
                        self.logger.info("⏳ 检测到非余额支付，请在浏览器中完成支付后，脚本将继续轮询状态...")
                    
                    headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
                    epay_headers = {
                        "User-Agent": headers["User-Agent"],
                        "Referer": f"https://buff.163.com/goods/{goods_id}?from=market",
                        "Cookie": self.profile.cookie # 必须携带 Cookie 否则支付授权会失效 (BillOrder Invalid)
                    }
                    # 模拟访问网易支付链接（这一步对某些支付流程是必须的，它会在服务端激活支付单）
                    requests.get(epay_url, headers=epay_headers, proxies=proxies, timeout=10)
            
            # 4. 关键修正：轮询检查支付状态 (收银台第四步)
            # 根据 HAR，只有当 state == 2 时才进行最后的 buy 调用
            # 给用户足够的时间去扫码或完成支付授权 (120秒)
            max_retries = 120
            is_ready = False
            for i in range(max_retries):
                check_res = self.batch_buy_check_state(batch_buy_id, goods_id)
                if not check_res or check_res.get("code") != "OK":
                    self.logger.warning(f"⚠️ [批量下单] 检查状态失败({i+1}/{max_retries}): {check_res}")
                else:
                    state = check_res.get("data", {}).get("state")
                    self.logger.info(f"🔄 [批量下单] 检查支付状态({i+1}/{max_retries}): state={state}")
                    if state == 2:
                        is_ready = True
                        break
                time.sleep(1.0) # 间隔1秒轮询
            
            if not is_ready:
                self.logger.error(f"❌ [批量下单] 支付状态未就绪 (Timeout)")
                return None

            # 5. 执行真正的下单请求 (携带 batch_buy_id)
            # 根据 HAR，如果是批量模式，最终还是调用 /api/market/goods/buy
            url = "/api/market/goods/buy"
            # 对于批量下单，sell_order_id 取第一个（Buff 内部逻辑，携带 batch_buy_id 后会自动处理列表）
            sell_order_id = sell_order_ids[0]
            
            headers = self.profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
            headers.update({
                "X-CSRFToken": self._get_csrf_token(),
                "Content-Type": "application/json",
            })

            payload = {
                "game": BuffGameType.CSGO.value,
                "goods_id": int(goods_id),
                "sell_order_id": sell_order_id,
                "price": str(total_price),
                "batch": 1,
                "pay_method": int(pay_method),
                "allow_tradable_cooldown": 0,
                "hide_non_epay": False,
                "batch_id": "",
                "batch_buy_id": batch_buy_id,
                "steamid": None
            }
            
            self.logger.info(f"🚀 [批量下单-最终确认] 发起创建支付单 POST {url} | BatchID={batch_buy_id}")
            response = requests.post(self.host + url, headers=headers, json=payload, proxies=proxies, timeout=10)
            response.raise_for_status()
            
            result = response.json()
            if result.get("code") == "OK":
                data = result.get("data", {})
                order_id = data.get("id")
                state = data.get("state_text", "已创建")
                self.logger.info(f"✨ [批量下单] 下单成功! 订单号: {order_id}, 状态: {state}")
                return data
            else:
                error_msg = result.get("error") or result.get("msg") or "未知错误"
                self.logger.error(f"❌ [批量下单] 最终确认失败: {result.get('code')} - {error_msg}")
                return result
            
            return result
                
        except Exception as e:
            self.logger.error(f"❌ [批量下单] 异常: {e}")
            return None

    def _get_csrf_token(self):
        """从 Cookie 中提取 CSRF Token"""
        try:
            cookie = SimpleCookie()
            cookie.load(self.profile.cookie or "")
            if "csrf_token" in cookie:
                token = cookie["csrf_token"].value
                # logger.debug(f"Extracted CSRF Token: {token[:10]}...")
                return token
        except Exception as e:
            logger.warning(f"解析 CSRF Token 失败: {e}")
        
        logger.warning("⚠️ 未能在 Cookie 中找到 csrf_token，下单可能会失败！")
        return ""


if __name__ == "__main__":
    # 测试代码
    spider = BuffSpider()
    items = spider.get_goods_list(1116002)
    print(json.dumps(items, ensure_ascii=False, indent=2))
