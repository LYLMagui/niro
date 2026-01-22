from enum import IntEnum, Enum

class BuffPaymentMethod(IntEnum):
    """BUFF 支付方式枚举"""
    BANK_CARD = 1
    ALIPAY = 3
    WECHAT = 6
    UNION_PAY = 7
    BALANCE = 44
    BUFF_BALANCE = 59

    @classmethod
    def get_label(cls, value: int) -> str:
        mapping = {
            cls.BANK_CARD: "银行卡",
            cls.ALIPAY: "支付宝",
            cls.WECHAT: "微信",
            cls.UNION_PAY: "银联",
            cls.BALANCE: "网易支付",
            cls.BUFF_BALANCE: "BUFF余额"
        }
        return mapping.get(value, f"未知({value})")

class BuffExterior(Enum):
    """BUFF 磨损程度枚举"""
    FACTORY_NEW = "wearcategory0"      # 崭新出厂
    MINIMAL_WEAR = "wearcategory1"     # 略有磨损
    FIELD_TESTED = "wearcategory2"     # 久经沙场
    WELL_WORN = "wearcategory3"        # 破损不堪
    BATTLE_SCARRED = "wearcategory4"   # 战痕累累
    NOT_PAINTED = "wearcategory5"      # 无涂装
    NONE = ""                          # 无磨损

    @classmethod
    def get_label(cls, value: str) -> str:
        mapping = {
            cls.FACTORY_NEW.value: "崭新出厂",
            cls.MINIMAL_WEAR.value: "略有磨损",
            cls.FIELD_TESTED.value: "久经沙场",
            cls.WELL_WORN.value: "破损不堪",
            cls.BATTLE_SCARRED.value: "战痕累累",
            cls.NOT_PAINTED.value: "无涂装",
            cls.NONE.value: "-"
        }
        return mapping.get(value, "-")

class BuffSortStrategy(Enum):
    """BUFF 排序策略"""
    PRICE_ASC = "price.asc"            # 价格从低到高
    PRICE_DESC = "price.desc"          # 价格从高到低
    CREATED_DESC = "created_at.desc"   # 最新发布
    PAINTWEAR_ASC = "paintwear.asc"    # 磨损从低到高
    PAINTWEAR_DESC = "paintwear.desc"  # 磨损从高到低

class BuffGameType(Enum):
    """BUFF 游戏类型"""
    CSGO = "csgo"
    DOTA2 = "dota2"
