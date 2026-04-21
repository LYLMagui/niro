export type InviteAdminStatus = 1 | 0;
export type InviteAvailability = "available" | "used" | "expired" | "disabled";

export interface InviteCodePageItem {
  id: number;
  code: string;
  status: InviteAdminStatus;
  availability: InviteAvailability;
  usedUserId?: number | null;
  registrationNickname?: string | null;
  registrationEmail?: string | null;
  registrationAccountStatus?: string | null;
  usedAt?: string | null;
  creatorName: string;
  forever: boolean;
  expireTime?: string | null;
  remark: string;
  createdAt: string;
}

export interface InviteCodeDetail {
  id: number;
  code: string;
  status: InviteAdminStatus;
  availability: InviteAvailability;
  issuerUserId: number;
  creatorName: string;
  usedUserId?: number | null;
  forever: boolean;
  registrationNickname?: string | null;
  registrationEmail?: string | null;
  registrationAccountStatus?: string | null;
  usedAt?: string | null;
  expireTime?: string | null;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

export interface InviteCodePageQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: InviteAdminStatus;
  availability?: InviteAvailability;
  issuerUserId?: number;
  startDate?: string;
  endDate?: string;
}

export interface InviteCodePageResult {
  records: InviteCodePageItem[];
  total: number;
  size: number;
  current: number;
}

export interface InviteCodeCreateParam {
  code?: string;
  expireTime?: string;
  forever?: boolean;
  remark?: string;
}

export interface InviteCodeBatchCreateParam {
  quantity: number;
  prefix?: string;
  expireTime?: string;
  forever?: boolean;
  remark?: string;
}

export interface InviteCodeBatchCreateResult {
  records: Array<{
    id: number;
    code: string;
    forever: boolean;
    expireTime?: string | null;
    remark: string;
  }>;
}

export interface InviteCodeUpdateParam {
  id: number;
  expireTime?: string;
  forever?: boolean;
  remark?: string;
}
