# AppKey 安全改造

## 目标

C5 扫货账号的 AppKey 从明文存储改为加密存储，并收敛前端明文暴露范围。

本改造覆盖：

- 数据库存储加密。
- 历史明文数据迁移。
- 列表接口脱敏展示。
- 编辑或点击“显示”时按需返回明文。
- 请求和响应中的 AppKey 字段做应用层加密。
- 业务调用 C5 时在服务端运行时解密，不落日志。

## 当前确认边界

- 前端采用“列表默认脱敏，编辑或点击显示时短暂展示明文”的方案。
- 数据库存储使用服务端 AES-256-GCM。
- HTTP 外层仍依赖 HTTPS；AppKey 字段额外使用 RSA-OAEP-256 做应用层加密。
- 后端业务内部允许短暂持有 AppKey 明文用于调用 C5 API，但禁止写入数据库、响应日志、业务日志或异常信息。
- 历史明文数据需要被迁移到密文字段；迁移完成后业务不再读取明文字段。

## 数据库存储设计

`c5_sniping_account` 新增字段：

- `c5_app_key_encrypted`：AES-GCM 密文，格式为 `v1:base64(iv):base64(ciphertextWithTag)`。
- `c5_app_key_masked`：脱敏展示值，例如 `abcd****wxyz`。
- `c5_app_key_migrated_at`：历史明文迁移完成时间。

保留既有 `c5_app_key` 字段作为短期历史兼容字段，只在迁移程序中读取；业务读写改为新字段。待确认所有环境迁移完成后，再单独发清理 migration 清空或删除旧字段。

服务端主密钥来源：

- 环境变量：`NIRO_APP_KEY_ENCRYPTION_KEY`
- 要求：32 字节随机密钥，使用 Base64 编码。
- 禁止写入代码、Git、SQL、镜像或日志。

## 传输加密设计

### 保存 AppKey

1. 前端调用后端公钥接口获取 RSA 公钥。
2. 前端使用 RSA-OAEP + SHA-256 加密 AppKey。
3. 后端使用 RSA 私钥解密请求字段。
4. 后端使用 AES-256-GCM 加密后写入数据库。
5. 后端同步写入脱敏值。

### 显示 AppKey

1. 前端生成一次性 RSA-OAEP 密钥对。
2. 前端将临时公钥提交给 reveal 接口。
3. 后端校验登录态与账号归属。
4. 后端解密数据库密文得到明文 AppKey。
5. 后端用前端临时公钥加密 AppKey 返回。
6. 前端用临时私钥解密并短暂展示，关闭弹窗、切页或超时后清空明文变量。

## 接口设计

### 获取后端 AppKey 加密公钥

`GET /api/c5/sniping/v2/accounts/app-key/public-key`

返回：

```json
{
  "algorithm": "RSA-OAEP-256",
  "publicKey": "base64(spki)"
}
```

### 列表接口

列表不再返回 `c5AppKey` 明文，改为返回：

```json
{
  "hasC5AppKey": true,
  "c5AppKeyMasked": "abcd****wxyz"
}
```

### 保存接口

保存入参不再接收明文 `c5AppKey`，改为：

```json
{
  "encryptedC5AppKey": "base64(ciphertext)"
}
```

编辑账号时如果用户未修改 AppKey，则不提交该字段，后端保留原密文。

### 显示接口

`POST /api/c5/sniping/v2/accounts/{id}/app-key/reveal`

请求：

```json
{
  "publicKey": "base64(spki)"
}
```

响应：

```json
{
  "algorithm": "RSA-OAEP-256",
  "encryptedC5AppKey": "base64(ciphertext)"
}
```

## 历史数据迁移

Flyway 只负责新增密文字段，不在 SQL 中执行加密。

应用启动后执行一次兼容迁移：

1. 查询 `c5_app_key` 非空且 `c5_app_key_encrypted` 为空的账号。
2. 使用服务端 AES-256-GCM 加密旧明文。
3. 写入 `c5_app_key_encrypted`、`c5_app_key_masked`、`c5_app_key_migrated_at`。
4. 迁移日志只记录账号数量，不记录明文或密文。

如果缺少 `NIRO_APP_KEY_ENCRYPTION_KEY`，应用应启动失败，避免继续以不安全方式运行。

## 业务调用约束

所有 C5 API 调用前都必须通过统一工具或服务解密 AppKey，不允许业务代码直接读取旧明文字段。

影响链路：

- C5 扫货账号配置。
- 批量刷新余额。
- 扫货任务扫描和下单。
- 订单同步、订单详情查询。
- C5 库存刷新、库存上架、手续费计算。

## 前端展示约束

- 列表只展示 `c5AppKeyMasked`。
- 点击显示或编辑弹窗按需调用 reveal 接口。
- 明文只保存在组件内局部变量，不进入 Pinia、localStorage、sessionStorage、URL、日志或错误提示。
- 展示倒计时结束、弹窗关闭、页面卸载时清空明文。

## 验证方式

- 后端编译通过。
- 新增 migration 只新增字段，不改历史脚本。
- 创建账号后数据库不出现新 AppKey 明文。
- 旧明文账号启动迁移后生成密文字段和脱敏字段。
- 列表接口不返回明文 AppKey。
- reveal 接口只对账号归属用户返回加密后的 AppKey。
- 余额刷新、库存刷新、扫货执行等 C5 调用链仍可正常读取 AppKey。
