from typing import List, Optional, Dict
from pydantic import BaseModel, Field, AliasPath

class BuffStickerInfo(BaseModel):
    """贴纸信息"""
    sticker_id: int = Field(alias="sticker_id")
    slot: int
    wear: float = 0.0
    name: str = ""

class BuffAssetInfo(BaseModel):
    paintwear: Optional[str] = None
    # 使用 AliasPath 提取深度嵌套字段
    rarity: Optional[str] = Field(None, validation_alias=AliasPath("info", "tags", "rarity", "internal_name"))
    exterior: Optional[str] = Field(None, validation_alias=AliasPath("info", "tags", "exterior", "internal_name"))
    stickers: List[BuffStickerInfo] = Field(default_factory=list, validation_alias=AliasPath("info", "stickers"))

class BuffGoodsInfo(BaseModel):
    name: str
    steam_price: Optional[str] = "0"
    steam_price_cny: Optional[str] = "0"
    sell_min_price: Optional[str] = "0"
    buy_max_price: Optional[str] = "0"
    sell_num: Optional[int] = 0

class BuffUserInfo(BaseModel):
    nickname: str

class BuffSellOrderItem(BaseModel):
    id: str
    goods_id: int
    price: str
    user_id: str
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
    sell_min_price: float = 0.0
    buy_max_price: float = 0.0
    sell_num: int = 0
    user_id: str
    user_nickname: str
    created_at: str
    crawled_at: str
    rarity: Optional[str] = None
    exterior: Optional[str] = None
    stickers: List[BuffStickerInfo] = Field(default_factory=list)
