from typing import List, Dict, Optional
from dto.buff_dto import BuffStickerInfo
from utils.logger import get_logger
import time

logger = get_logger(__name__)

class PremiumCalculator:
    """印花溢价计算器"""
    
    # 缓存印花价格，减少数据库查询次数 (sticker_id -> {"price": float, "timestamp": int})
    _price_cache: Dict[int, Dict] = {}
    CACHE_EXPIRE = 3600  # 1小时过期

    @classmethod
    async def get_sticker_price(cls, sticker_id: int) -> float:
        """获取印花市场价格（带缓存，异步版）"""
        now = int(time.time())
        if sticker_id in cls._price_cache:
            cache_item = cls._price_cache[sticker_id]
            if now - cache_item["timestamp"] < cls.CACHE_EXPIRE:
                return cache_item["price"]

        # 缓存不存在或已过期
        # V2.4.0 移除 Python 端数据库查询，后续需改为从 Redis 获取或由 Java 端下发
        return 0.0

    @classmethod
    async def calculate_item_sticker_value(cls, stickers: List[BuffStickerInfo]) -> Dict:
        """
        计算饰品上的印花总价值与理论溢价 (异步版)
        :param stickers: 饰品携带的贴纸列表
        :return: {
            "total_sticker_price": 贴纸总原价,
            "theoretical_premium": 理论溢价额,
            "details": [每个贴纸的贡献详情]
        }
        """
        total_price = 0.0
        total_premium = 0.0
        details = []

        for s in stickers:
            base_price = await cls.get_sticker_price(s.sticker_id)
            if base_price <= 0:
                continue

            # 基础溢价率算法 (简易版)
            # 1. 昂贵印花溢价率更高 (如卡托2014)
            if base_price >= 10000:
                premium_rate = 0.10  # 10%
            elif base_price >= 1000:
                premium_rate = 0.05  # 5%
            elif base_price >= 100:
                premium_rate = 0.02  # 2%
            else:
                premium_rate = 0.01  # 1%

            # 2. 磨损惩罚 (刮过的贴纸几乎无溢价)
            # Buff 的 wear 为 0.0 代表无损，1.0 代表全刮
            if s.wear > 0:
                # 即使刮一点点，价值也会大幅缩水
                premium_rate = premium_rate * (1 - s.wear) * 0.1 

            contribution = base_price * premium_rate
            
            total_price += base_price
            total_premium += contribution
            
            details.append({
                "name": s.name,
                "base_price": base_price,
                "contribution": contribution,
                "wear": s.wear
            })

        return {
            "total_sticker_price": round(total_price, 2),
            "theoretical_premium": round(total_premium, 2),
            "details": details
        }

    @classmethod
    async def evaluate_item_premium(cls, current_price: float, market_floor: float, stickers: List[BuffStickerInfo]) -> tuple[bool, str]:
        """
        评估饰品溢价是否合理（即是否为捡漏，异步版）
        :return: (是否匹配, 匹配理由)
        """
        if not stickers:
            return False, ""

        res = await cls.calculate_item_sticker_value(stickers)
        theoretical_premium = res["theoretical_premium"]
        
        if theoretical_premium <= 0:
            return False, ""

        # 理论合理价格 = 底价 + 理论溢价
        theoretical_fair_price = market_floor + theoretical_premium
        
        # 实际溢价 = 当前价 - 底价
        actual_premium = current_price - market_floor
        
        # 捡漏标准：
        # 1. 当前价 <= 理论公平价
        # 2. 实际溢价 < 理论溢价的 70% (留出 30% 的安全/利润空间)
        if current_price <= theoretical_fair_price and actual_premium < (theoretical_premium * 0.7):
            reason = f"印花总价:¥{res['total_sticker_price']}, 理论溢价:¥{theoretical_premium:.2f}, 实际溢价:¥{max(0, actual_premium):.2f}"
            return True, reason
        
        return False, ""
