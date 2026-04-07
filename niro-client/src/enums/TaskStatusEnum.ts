export enum TaskStatusEnum {
  STOPPED = 0,
  RUNNING = 1,
  COMPLETED = 2,
  ERROR = 3,
  SYSTEM_RUNNING = 4,
  SCHEDULED = 5,
}

export const TaskStatusMap = {
  [TaskStatusEnum.STOPPED]: { label: "已停止", color: "default" },
  [TaskStatusEnum.RUNNING]: { label: "运行中", color: "success" },
  [TaskStatusEnum.COMPLETED]: { label: "已完成", color: "primary" },
  [TaskStatusEnum.ERROR]: { label: "异常", color: "danger" },
  [TaskStatusEnum.SYSTEM_RUNNING]: { label: "系统运行中", color: "warning" },
  [TaskStatusEnum.SCHEDULED]: { label: "定时等待中", color: "warning" },
};

export const ACTIVE_TASK_STATUSES = [
  TaskStatusEnum.RUNNING,
  TaskStatusEnum.SYSTEM_RUNNING,
  TaskStatusEnum.SCHEDULED,
] as const;

export const STARTABLE_TASK_STATUSES = [TaskStatusEnum.STOPPED, TaskStatusEnum.ERROR] as const;

export const isActiveTaskStatus = (
  status?: number
): status is (typeof ACTIVE_TASK_STATUSES)[number] =>
  status !== undefined &&
  ACTIVE_TASK_STATUSES.includes(status as (typeof ACTIVE_TASK_STATUSES)[number]);

export const isStartableTaskStatus = (
  status?: number
): status is (typeof STARTABLE_TASK_STATUSES)[number] =>
  status !== undefined &&
  STARTABLE_TASK_STATUSES.includes(status as (typeof STARTABLE_TASK_STATUSES)[number]);
