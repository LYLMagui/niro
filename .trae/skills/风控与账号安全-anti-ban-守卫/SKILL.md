---
name: 风控与账号安全 (Anti-Ban) 守卫
description: 操作 BuffAccount、修改网络请求层、处理 Cookie 或 UserAgent。
---

# 任务：Niro 账号与风控安全处理
## 核心 SOP：
1. **凭证隔离**：严禁在代码中硬编码任何 `buffCookie`。所有凭证操作必须通过 `AccountService` 封装。
2. **熔断检查**：任何调用外部接口的地方，必须捕获异常并同步更新 `failCount`。如果连续失败 5 次，必须触发 `status` 自动置为“异常”。
3. **脱敏要求**：在生成日志记录代码时，必须对 Cookie 和敏感账号信息进行 MD5 或星号脱敏。
4. **UA 绑定**：确保每个请求都正确绑定了该账号对应的 `userAgent`，严禁所有账号共用同一个 UA。