# 部署指南

## 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 推荐 JDK 21 |
| Maven | 3.8+ | 构建工具 |
| PostgreSQL | 14+ | 数据库 |
| Keycloak | 20+ | 认证服务（可选） |

## 配置

### 应用配置

通过 `-Dvertx-config-path` 或环境变量传入：

```json
{
  "http.port": 8888,
  "context.path": "",
  "profile": "dev",
  "scheduler.enabled": true,

  "db.host": "localhost",
  "db.port": 5432,
  "db.database": "vertx_app",
  "db.user": "postgres",
  "db.password": "postgres",
  "db.pool.size": 10,
  "db.ssl": false,

  "auth.enabled": false,
  "auth.keycloak.issuer": "http://localhost:8080/realms/vertx-app",
  "auth.keycloak.clientId": "vertx-app",
  "auth.keycloak.jwksPath": "/protocol/openid-connect/certs"
}
```

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `http.port` | 8888 | HTTP 端口 |
| `context.path` | `""` | 上下文路径（如 `/support`） |
| `profile` | `""` | 运行环境标识 |
| `scheduler.enabled` | `true` | 是否启用定时任务 |
| `db.host` | `localhost` | 数据库主机 |
| `db.port` | 5432 | 数据库端口 |
| `db.database` | `vertx_app` | 数据库名 |
| `db.user` | `postgres` | 数据库用户 |
| `db.password` | `postgres` | 数据库密码 |
| `db.pool.size` | 10 | 连接池大小 |
| `db.ssl` | `false` | 是否启用 SSL |
| `auth.enabled` | `false` | 是否启用认证 |

## 本地开发

### 1. 启动 PostgreSQL

```bash
# Docker
docker run -d --name vertx-pg \
  -e POSTGRES_DB=vertx_app \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

### 2. 编译

```bash
mvn clean compile -DskipTests
```

### 3. 运行

```bash
# 默认配置
mvn exec:java -Dexec.mainClass="com.example.App"

# 自定义配置
mvn exec:java -Dexec.mainClass="com.example.App" \
  -Dvertx-config-path=config-dev.json
```

### 4. Demo 模式

不配置数据库时自动进入 Demo 模式：
- 所有读操作返回空数据或示例数据
- 写操作返回错误

## 生产部署

### JAR 打包

```bash
mvn clean package -DskipTests
java -jar target/vertx-app-1.0.0-fat.jar -conf config-prod.json
```

### Docker 部署

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/vertx-app-1.0.0-fat.jar app.jar
COPY config-prod.json config.json
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar", "-conf", "config.json"]
```

```bash
docker build -t vertx-app .
docker run -d -p 8888:8888 \
  -e DB_HOST=db.internal \
  -e DB_PASSWORD=secure_password \
  vertx-app
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vertx-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vertx-app
  template:
    metadata:
      labels:
        app: vertx-app
    spec:
      containers:
      - name: vertx-app
        image: vertx-app:latest
        ports:
        - containerPort: 8888
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: host
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8888
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8888
          initialDelaySeconds: 10
          periodSeconds: 5
```

## Keycloak 配置

### 1. 创建 Realm

```
Realm Name: vertx-app
```

### 2. 创建 Client

```
Client ID: vertx-app
Valid Redirect URIs: http://localhost:*
Web Origins: +
```

### 3. 创建角色

按业务需要创建，例如：
- `admin` — 管理员
- `user` — 普通用户

### 4. 启用认证

```json
{
  "auth.enabled": true,
  "auth.keycloak.issuer": "https://keycloak.example.com/realms/vertx-app",
  "auth.keycloak.clientId": "vertx-app"
}
```

## 数据库迁移

Flyway 在应用启动时自动执行迁移。迁移文件位于：

```
src/main/resources/db/migration/
├── V1__init_schema.sql
├── V2__seed_data.sql
├── ...
└── V12__rbac_tables.sql
```

**注意：** 已执行的迁移不可修改，只能新增。

## 监控

### 健康检查

```bash
curl http://localhost:8888/health
```

### 日志

日志配置在 `src/main/resources/logback.xml`，输出到 stdout。

### 关键日志标识

| 标识 | 说明 |
|------|------|
| `[OK]` | 操作成功 |
| `[WARN]` | 警告（非致命） |
| `[FAIL]` | 操作失败 |
| `[AUTH]` | 认证相关 |
| `[DB]` | 数据库相关 |
| `[TX-TEMPLATE]` | 事务相关 |
| `[SCHEDULER]` | 调度器相关 |

## 常见问题

### Q: 启动报 "Port 8888 in use"

A: 应用会自动尝试 8889、8890... 直到找到可用端口。检查日志中的实际端口号。

### Q: Demo 模式下数据不可写

A: 这是预期行为。Demo 模式（`dbAvailable = false`）下所有写操作返回错误，读操作返回空数据或示例数据。

### Q: Keycloak 连接失败

A: 应用会在 Keycloak 不可用时降级为无认证模式，日志中会看到 `[AUTH] Continuing without authentication`。

### Q: 数据库迁移失败

A: 检查 Flyway 迁移文件是否正确，已执行的迁移不可修改。
