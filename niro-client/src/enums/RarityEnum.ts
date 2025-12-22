export enum RarityEnum {
  // 武器
  COMMON = 'common_weapon', // 消费级
  UNCOMMON = 'uncommon_weapon', // 工业级
  RARE = 'rare_weapon', // 军规级
  MYTHICAL = 'mythical_weapon', // 受限
  LEGENDARY = 'legendary_weapon', // 保密
  ANCIENT = 'ancient_weapon', // 隐秘
  IMMORTAL = 'immortal', // 违禁

  // 探员
  ANCIENT_CHARACTER = 'ancient_character', // 大师级
  LEGENDARY_CHARACTER = 'legendary_character', // 非凡
  MYTHICAL_CHARACTER = 'mythical_character', // 卓越
  RARE_CHARACTER = 'rare_character', // 高级

  // 印花/道具
  EXTRAORDINARY = 'unusual', // 非凡
  ANCIENT_ITEM = 'ancient', // 非凡
  EXOTIC = 'legendary', // 奇异
  REMARKABLE = 'mythical', // 卓越
  HIGH_GRADE = 'rare', // 高级
  NORMAL = 'common', // 普通级
}

export const RarityMap: Record<string, string> = {
  [RarityEnum.COMMON]: '消费级',
  [RarityEnum.UNCOMMON]: '工业级',
  [RarityEnum.RARE]: '军规级',
  [RarityEnum.MYTHICAL]: '受限',
  [RarityEnum.LEGENDARY]: '保密',
  [RarityEnum.ANCIENT]: '隐秘',
  [RarityEnum.IMMORTAL]: '违禁',

  [RarityEnum.ANCIENT_CHARACTER]: '大师级',
  [RarityEnum.LEGENDARY_CHARACTER]: '非凡',
  [RarityEnum.MYTHICAL_CHARACTER]: '卓越',
  [RarityEnum.RARE_CHARACTER]: '高级',

  [RarityEnum.EXTRAORDINARY]: '非凡',
  [RarityEnum.ANCIENT_ITEM]: '非凡',
  [RarityEnum.EXOTIC]: '奇异',
  [RarityEnum.REMARKABLE]: '卓越',
  [RarityEnum.HIGH_GRADE]: '高级',
  [RarityEnum.NORMAL]: '普通级',
};

// 品质颜色 (参考 CSGO 官方/Buff)
export const RarityColorMap: Record<string, string> = {
  // 基础武器颜色
  [RarityEnum.COMMON]: '#b0c3d9', // 白/灰
  [RarityEnum.UNCOMMON]: '#5e98d9', // 浅蓝
  [RarityEnum.RARE]: '#4b69ff', // 蓝
  [RarityEnum.MYTHICAL]: '#8847ff', // 紫
  [RarityEnum.LEGENDARY]: '#d32ce6', // 粉
  [RarityEnum.ANCIENT]: '#eb4b4b', // 红
  [RarityEnum.IMMORTAL]: '#e4ae39', // 金

  // 探员颜色
  [RarityEnum.ANCIENT_CHARACTER]: '#eb4b4b', // 红
  [RarityEnum.LEGENDARY_CHARACTER]: '#d32ce6', // 粉
  [RarityEnum.MYTHICAL_CHARACTER]: '#8847ff', // 紫
  [RarityEnum.RARE_CHARACTER]: '#4b69ff', // 蓝

  // 印花/道具颜色
  [RarityEnum.EXTRAORDINARY]: '#eb4b4b', // 红 (有些也是金 #e4ae39)
  [RarityEnum.ANCIENT_ITEM]: '#eb4b4b', // 红
  [RarityEnum.EXOTIC]: '#d32ce6', // 粉
  [RarityEnum.REMARKABLE]: '#8847ff', // 紫
  [RarityEnum.HIGH_GRADE]: '#4b69ff', // 蓝
  [RarityEnum.NORMAL]: '#b0c3d9', // 白
};
