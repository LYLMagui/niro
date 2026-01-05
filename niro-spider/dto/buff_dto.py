from typing import List, Optional, Dict
from pydantic import BaseModel, Field

class BuffAssetInfo(BaseModel):
    paintwear: Optional[str] = None
    # 可以根据需要添加更多字段

class BuffGoodsInfo(BaseModel):
    name: str
    steam_price: str
    steam_price_cny: str
    sell_min_price: str
    buy_max_price: str
    sell_num: int

class BuffUserInfo(BaseModel):
    nickname: str

class BuffSellOrderItem(BaseModel):
    id: str
    goods_id: int
    price: str
    user_id: int
    created_at: int
    asset_info: BuffAssetInfo

class BuffSellOrderData(BaseModel):
    items: List[BuffSellOrderItem]
    goods_infos: Dict[str, BuffGoodsInfo]
    user_infos: Dict[str, BuffUserInfo]

class BuffSellOrderResponse(BaseModel):
    code: str
    data: Optional[BuffSellOrderData] = None
    msg: Optional[str] = None

class ParsedBuffItemDTO(BaseModel):
    id: str
    goods_id: int
    name: str
    price_usd: str
    price_cny: str
    paintwear: Optional[str] = None
    price_buff: Optional[str] = None
    sell_min_price: float
    buy_max_price: float
    sell_num: int
    user_id: int
    user_nickname: str
    created_at: str
    crawled_at: str
