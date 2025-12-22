export enum ExteriorEnum {
  FACTORY_NEW = 'wearcategory0', // 崭新出厂
  MINIMAL_WEAR = 'wearcategory1', // 略有磨损
  FIELD_TESTED = 'wearcategory2', // 久经沙场
  WELL_WORN = 'wearcategory3', // 破损不堪
  BATTLE_SCARRED = 'wearcategory4', // 战痕累累
  NOT_PAINTED = 'wearcategory5', // 无涂装
  NONE = '', // 无磨损 (印花、箱子等)
}

export const ExteriorMap: Record<string, string> = {
  [ExteriorEnum.FACTORY_NEW]: '崭新出厂',
  [ExteriorEnum.MINIMAL_WEAR]: '略有磨损',
  [ExteriorEnum.FIELD_TESTED]: '久经沙场',
  [ExteriorEnum.WELL_WORN]: '破损不堪',
  [ExteriorEnum.BATTLE_SCARRED]: '战痕累累',
  [ExteriorEnum.NOT_PAINTED]: '无涂装',
  [ExteriorEnum.NONE]: '-',
};

// 用于下拉选择框的选项列表
export const ExteriorOptions = [
  { label: '崭新出厂', value: ExteriorEnum.FACTORY_NEW },
  { label: '略有磨损', value: ExteriorEnum.MINIMAL_WEAR },
  { label: '久经沙场', value: ExteriorEnum.FIELD_TESTED },
  { label: '破损不堪', value: ExteriorEnum.WELL_WORN },
  { label: '战痕累累', value: ExteriorEnum.BATTLE_SCARRED },
  { label: '无涂装', value: ExteriorEnum.NOT_PAINTED },
];
