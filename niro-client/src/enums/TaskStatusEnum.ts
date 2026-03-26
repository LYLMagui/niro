export enum TaskStatusEnum {
  STOPPED = 0,
  RUNNING = 1,
  COMPLETED = 2,
  ERROR = 3,
  SYSTEM_RUNNING = 4,
}

export const TaskStatusMap = {
  [TaskStatusEnum.STOPPED]: { label: "已停止", color: "default" },
  [TaskStatusEnum.RUNNING]: { label: "运行中", color: "success" },
  [TaskStatusEnum.COMPLETED]: { label: "已完成", color: "primary" },
  [TaskStatusEnum.ERROR]: { label: "异常", color: "danger" },
  [TaskStatusEnum.SYSTEM_RUNNING]: { label: "执行中", color: "warning" },
};
