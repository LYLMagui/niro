export enum TaskRunModeEnum {
  SCAN = "SCAN",
  TRADE = "TRADE",
  BOTH = "BOTH",
}

export const TaskRunModeMap = {
  [TaskRunModeEnum.SCAN]: "仅扫描",
  [TaskRunModeEnum.TRADE]: "仅下单",
  [TaskRunModeEnum.BOTH]: "全能模式",
};
