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