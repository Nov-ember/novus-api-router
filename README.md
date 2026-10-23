# Novus API Router

基于 Java 的大模型 API 中转站，提供统一的文本对话接口、API Key 管理、按 token 计费和缓存感知路由。

**技术栈：** Java 17 · Spring Boot 4.1.1 · Spring AI 2.0.1 · MyBatis-Plus · MySQL · Redis

## 功能

- 支持多渠道、模型别名、非流式与 SSE 流式对话。
- 支持注册登录、API Key 创建与吊销、管理员增加测试余额。
- 调用前预留预算，按实际用量结算，保存价格快照。
- 基于会话绑定和请求前缀选择路由，统计缓存命中率与费用。

## 启动

按照 [Docker 部署](docker/README.md) 配置并启动。

## 接口使用

默认地址：`http://localhost:8080`。

### 1. 获取调用凭证

按顺序完成以下操作。JWT 和 API Key 都取自响应的 `data` 字段。

| 操作 | 方法与路径 | Authorization 请求头 | JSON 请求体 |
| --- | --- | --- | --- |
| 注册，获取 JWT | `POST /auth/register` | 不需要 | `{"username":"demo","email":"demo@example.com","password":"DemoPass123"}` |
| 为用户增加余额 | `POST /wallet/{userId}` | `Bearer <管理员 JWT>` | `10.00` |
| 创建 API Key | `POST /api-keys` | `Bearer <用户 JWT>` | `{"name":"demo-key"}` |

新账号余额为 0。`{userId}` 替换为该用户在数据库 `user` 表中的 ID；管理员 ID 由 `ADMIN_USER_IDS` 配置。已有账号通过 `POST /auth/login` 登录，请求体为 `{"username":"demo","password":"DemoPass123"}`，无需鉴权。

### 2. 调用模型

**`POST /v1/chat/completions`**

| 请求头 | 值 | 是否必填 |
| --- | --- | --- |
| `Authorization` | `Bearer <上一步创建的 API Key>` | 是 |
| `Content-Type` | `application/json` | 是 |
| `X-Session-Id` | `demo-session-01` | 否 |

JSON 请求体：

```json
{
  "model": "deepseek-flash",
  "messages": [{"role": "user", "content": "用一句话解释数据库事务。"}],
  "stream": false,
  "max_completion_tokens": 256
}
```

`model`、`messages` 必填。`stream` 可省略，设为 `true` 时返回 SSE；`max_completion_tokens` 可省略，默认 4096。多轮对话在 `messages` 中携带历史，角色支持 `system`、`user`、`assistant`。

非流式回答在 `choices[0].message.content`，用量在 `usage`；流式内容在 `choices[0].delta.content`，正常结束发送 `[DONE]`。

### 3. 查询与管理

以下接口统一使用 `Authorization: Bearer <用户 JWT>`，只操作当前用户的数据。

| 方法与路径 | 请求体 / Query 参数 | 用途                              |
| --- | --- |---------------------------------|
| `GET /wallet` | 无 | 查询总余额                           |
| `GET /api-keys` | 无 | 获取有效 Key 列表                     |
| `PATCH /api-keys/{id}` | JSON：`{"name":"new-name"}` | 重命名 Key                         |
| `DELETE /api-keys/{id}` | 无 | 吊销 Key                          |
| `GET /bill/statistics` | Query：`startTime`、`endTime` 必填；`apiKeyId`、`modelAlias` 可选 | 查询时间范围内已结算请求数、token 用量、缓存命中率和费用 |

统计时间使用 `2026-10-01T00:00:00` 格式，范围为 `[startTime, endTime)`。
管理接口返回 `{"code":1,"msg":null,"data":...}`；业务失败时 `code=0`，原因在 `msg`。模型接口直接返回对话响应，不使用此包装。

## 模型与价格

当前配置 qwen-01 和 deepseek-01 两个渠道。模型映射和价格在 [application.yaml](src/main/resources/application.yaml) 中配置，调用方使用模型别名。

| 模型别名 | 渠道 → 上游模型 |
| --- | --- |
| `qwen3.8-flash` | `qwen-01` → `qwen3.8-flash` |
| `deepseek-flash` | `deepseek-01` → `deepseek-flash`；`qwen-01` → `deepseek-v4.1-flash` |

中转站配置价格，单位为 **元 / 百万 token**；同一别名使用相同价格。

| 模型别名 | 普通输入 | 缓存输入 | 输出 |
| --- | ---: | ---: | ---: |
| `qwen3.8-flash` | 0.8 | 0.1 | 2.7 |
| `deepseek-flash` | 1 | 0.02 | 4 |

费用 = `((输入量 − 缓存输入量) × 普通输入单价 + 缓存输入量 × 缓存单价 + 输出量 × 输出单价) / 1,000,000`。

## 测试结果

测试日期：2026-10-22。对克隆仓库执行 Docker 镜像构建并启动 Compose 服务，使用配置的千问和 DeepSeek 上游账号调用。以下为实际响应和账单数据；金额单位为元。

| 渠道 / 模型别名 | 调用方式 | 输入 token | 缓存输入 token | 输出 token | 结算金额 | 结果 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 千问 / `qwen3.8-flash` | 非流式 | 67 | 0 | 116 | 0.00036680 | 成功，返回用量 |
| 千问 / `qwen3.8-flash` | 流式 | 67 | 0 | 72 | 0.00024800 | 成功，收到 `[DONE]` 和用量 |
| DeepSeek / `deepseek-flash` | 非流式 | 37 | 0 | 74 | 0.00033300 | 成功，返回用量 |
| DeepSeek / `deepseek-flash` | 流式 | 37 | 0 | 83 | 0.00036900 | 成功，收到 `[DONE]` 和用量 |

缓存对比使用同一 API Key、DeepSeek 渠道、相同的超过 1024 字符前缀及 `X-Session-Id`，两次均为非流式请求：

| 请求 | 输入 token | 缓存输入 token | 输出 token | 结算金额 |
| --- | ---: | ---: | ---: | ---: |
| 第一次 | 318 | 0 | 48 | 0.00051000 |
| 重复相同前缀 | 318 | 128 | 48 | 0.00038456 |

第二次命中 128 个缓存输入 token，命中率为 40.25%，本次请求费用减少 0.00012544 元。千问相同前缀重复请求未报告缓存 token。

注册、登录、管理员充值、API Key 创建/查询/重命名/吊销及账单统计均通过。无凭证、将 JWT 用作模型 API Key、吊销后的 Key 均返回 401；普通用户充值被拒绝，零余额模型调用返回 429。统计接口按模型别名返回的 4 笔请求金额分别为：`qwen3.8-flash` 0.00179310 元、`deepseek-flash` 0.00159656 元，与账单明细合计一致。

## 缓存策略与数据结构

路由优先级为 **有效会话绑定 → 历史请求前缀 → 随机候选**。同一会话沿用相同的 `X-Session-Id`；实际缓存命中由上游决定，命中率按缓存输入 token 占总输入 token 的比例计算。

MySQL 保存用户（`user`）、API Key（`api_key`）、钱包（`user_wallet`）和账单（`bill`），完整结构见 [建表 SQL](docker/mysql/init/schema.sql)。会话绑定和前缀路由记录存于 Redis。

## 实现范围与限制

- 已实现文本多轮对话、流式转发、渠道配置、用量计费、会话粘性和缓存感知路由；统计按 API Key 和模型别名查询。
- 当前版本暂不提供对账和显式缓存接入。统计仅支持 API Key 和模型别名维度，不提供平台维度统计。
- 暂未实现故障转移、请求幂等、限流熔断等加分项。
- 上游用量缺失或流式中断时，若账单尚未结算，系统会尝试释放预留金额，不向用户扣费，已产生的上游调用成本由中转站承担。进程异常退出后的冻结资金暂需人工处理。
