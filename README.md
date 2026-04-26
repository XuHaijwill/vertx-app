# My Vert.x Application 🚀

A reactive, enterprise-grade REST API built with Eclipse Vert.x 4.x

## ✨ Features

- **🔌 RESTful API** - Clean, structured endpoints
- **📦 Unified Response** - Consistent JSON response format
- **🔒 Error Handling** - Centralized exception handling
- **🌐 CORS Support** - Cross-origin requests enabled
- **📊 Health Checks** - Kubernetes-ready probes
- **⚡ Reactive** - Non-blocking I/O with Vert.x
- **🧪 Test Ready** - JUnit 5 integration tests

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+

### Install Maven

```powershell
# Windows (Scoop)
scoop install maven

# Windows (Chocolatey)
choco install maven
```

### Run

```bash
# Navigate to project
cd my-vertx-app

# Run in development mode
mvn vertx:run

# Or run directly
mvn compile exec:java -Dexec.mainClass="com.example.App"
```

Server starts at: **http://localhost:8888**

---

## 📁 Project Structure

```
my-vertx-app/
├── pom.xml
├── config.json
├── Dockerfile
└── src/main/java/com/example/
    ├── App.java                      # Main entry point
    ├── MainVerticle.java             # HTTP server & routes
    ├── core/
    │   ├── ApiResponse.java          # Unified response wrapper
    │   ├── BusinessException.java    # Business exceptions
    │   ├── PageResult.java           # Pagination result
    │   └── RequestValidator.java     # Request validation
    ├── service/
    │   ├── UserService.java          # User service interface
    │   ├── UserServiceImpl.java      # User service impl
    │   ├── ProductService.java       # Product service interface
    │   └── ProductServiceImpl.java   # Product service impl
    └── verticles/
        └── DatabaseVerticle.java     # Database verticle
```

---

## 🔌 API Endpoints

### Health Checks

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Full health status |
| GET | `/health/live` | K8s liveness probe |
| GET | `/health/ready` | K8s readiness probe |

### Users API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | List all users |
| GET | `/api/users/search?q=` | Search users |
| GET | `/api/users/:id` | Get user by ID |
| POST | `/api/users` | Create user |
| PUT | `/api/users/:id` | Update user |
| DELETE | `/api/users/:id` | Delete user |

### Products API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List all products |
| GET | `/api/products/search?q=&category=` | Search products |
| GET | `/api/products/:id` | Get product by ID |
| POST | `/api/products` | Create product |
| PUT | `/api/products/:id` | Update product |
| DELETE | `/api/products/:id` | Delete product |

---

## 📋 Request/Response Examples

### Health Check

```bash
curl http://localhost:8888/health
```

**Response:**
```json
{
  "code": "success",
  "message": "操作成功",
  "data": {
    "status": "UP",
    "service": "my-vertx-app",
    "version": "1.0.0",
    "timestamp": 1714112345678,
    "uptime": "5m 32s",
    "memory": {
      "total": "512MB",
      "used": "234MB",
      "free": "278MB",
      "percentage": "45.7%"
    }
  },
  "timestamp": 1714112345678
}
```

### Create User

```bash
curl -X POST http://localhost:8888/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","age":30,"department":"Engineering"}'
```

**Response:**
```json
{
  "code": "success",
  "message": "User created",
  "data": {
    "id": 4,
    "name": "John Doe",
    "email": "john@example.com",
    "age": 30,
    "department": "Engineering",
    "status": "active",
    "createdAt": "2026-04-26T07:30:00Z"
  },
  "timestamp": 1714112400000
}
```

### Get User

```bash
curl http://localhost:8888/api/users/1
```

**Response:**
```json
{
  "code": "success",
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "Alice",
    "email": "alice@example.com",
    "age": 28,
    "department": "Engineering",
    "status": "active",
    "createdAt": "2026-04-26T07:00:00Z"
  },
  "timestamp": 1714112345000
}
```

### Search Users

```bash
curl "http://localhost:8888/api/users/search?q=alice"
```

### Update User

```bash
curl -X PUT http://localhost:8888/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Smith","age":29}'
```

### Delete User

```bash
curl -X DELETE http://localhost:8888/api/users/1
```

---

## ⚙️ Configuration

Edit `config.json`:

```json
{
  "http.port": 8888,
  "db.host": "localhost",
  "db.port": 3306,
  "db.database": "mydb",
  "db.user": "root",
  "db.password": ""
}
```

### Environment Variables

```bash
# Override via environment
export HTTP_PORT=9999
export DB_HOST=mydb.example.com
java -jar target/my-vertx-app-1.0.0-SNAPSHOT.jar
```

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

---

## 🐳 Docker

### Build & Run

```bash
# Build image
docker build -t my-vertx-app .

# Run container
docker run -p 8888:8888 my-vertx-app
```

### Docker Compose

```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8888:8888"
    environment:
      - DB_HOST=db
      - DB_PORT=3306
```

---

## 📦 Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| vertx-core | 4.5.13 | Core Vert.x |
| vertx-web | 4.5.13 | Web framework |
| vertx-jdbc-client | 4.5.13 | Database |
| logback | 1.5.12 | Logging |
| junit-jupiter | 5.11.4 | Testing |

---

## 📚 Resources

- [Vert.x Documentation](https://vertx.io/docs/)
- [Vert.x Web](https://vertx.io/docs/vertx-web/java/)
- [Vert.x JDBC Client](https://vertx.io/docs/vertx-jdbc-client/java/)

---

## 📝 License

MIT License