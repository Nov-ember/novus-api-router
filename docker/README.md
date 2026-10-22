# Docker 部署

使用 Docker Compose 启动后端、MySQL 和 Redis。

## 使用 Compose 部署

### 1. 环境准备

安装并启动 Docker，确保可以使用 `docker compose` 命令。

### 2. 配置环境变量

进入项目根目录下的 `docker` 目录，将 `.env.example` 复制为 `.env`：

```bash
cd docker
cp .env.example .env
```

在 `.env` 中填写必填缺失字段，并按需修改已有默认值。

### 3. 启动服务

在 `docker` 目录执行：

```bash
docker compose up --build -d
```

### 4. 初始化与管理员配置

MySQL 首次启动且数据目录为空时，会自动执行 [schema.sql](mysql/init/schema.sql) 建表，无需手动导入。已有数据卷不会重复执行初始化脚本。

服务启动后，按 [接口说明](../README.md#接口使用) 注册账号，再查询该账号的用户 ID。在 `docker` 目录执行：

```bash
docker compose exec mysql mysql -u root -p
```

输入 `.env` 中的 `MYSQL_ROOT_PASSWORD`，执行以下 SQL；如果修改过 `MYSQL_DB`，将数据库名替换为实际值：

```sql
USE novus_api_router;
SELECT id, username FROM user;
```
查询完成后，输入 exit 退出 MySQL 客户端。

将要设为管理员的账号 ID 填入 `.env` 的 `ADMIN_USER_IDS`，多个 ID 用英文逗号分隔。该配置不会创建账号；模板中的 ID 只是示例，需按实际账号调整。

修改后，在 `docker` 目录执行以下命令，使应用使用新的管理员配置：

```bash
docker compose up -d app
```

使用该管理员账号登录获得 JWT，即可调用 `POST /wallet/{userId}` 为目标用户增加测试余额；`userId` 使用上面查询到的目标用户 ID，也可以为管理员自己增加余额。
