# Task Manager REST API

[![Java CI with Maven](https://github.com/EliBen8/taskmanager/actions/workflows/ci.yml/badge.svg)](https://github.com/EliBen8/taskmanager/actions/workflows/ci.yml)

A production-ready REST API for managing tasks and projects, built with Spring Boot and PostgreSQL. Features JWT-based stateless authentication, task ownership, schema-versioned migrations via Flyway, MapStruct DTO mapping, and a comprehensive test suite.

## 🚀 Features

- ✅ JWT-based stateless authentication (register / login)
- ✅ Task ownership — each user sees and manages only their own tasks
- ✅ Project management — group tasks under named projects
- ✅ Full CRUD for tasks and projects with ownership enforcement
- ✅ Multi-state task lifecycle: `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`, `CANCELLED`
- ✅ Schema migrations managed by Flyway (V1–V5)
- ✅ MapStruct compile-time DTO mapping (no runtime reflection)
- ✅ Bean Validation on all request payloads
- ✅ Structured JSON error responses via `GlobalExceptionHandler`
- ✅ Comprehensive test suite (25+ tests — unit, slice, integration)
- ✅ CI/CD pipeline with GitHub Actions
- ✅ Docker & Docker Compose support

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.7 |
| Security | Spring Security + JJWT 0.12.x (HMAC-SHA256) |
| Database | PostgreSQL 15 |
| Migrations | Flyway |
| Mapping | MapStruct 1.5.5 |
| Validation | Jakarta Bean Validation |
| Build | Maven |
| Testing | JUnit 5, Mockito, MockMvc, jqwik |
| Containers | Docker & Docker Compose |
| CI/CD | GitHub Actions |

## 📋 API Endpoints

### Authentication (public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user, returns JWT |
| POST | `/api/auth/login` | Authenticate, returns JWT |

### Tasks (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tasks` | List authenticated user's tasks |
| GET | `/api/tasks/{id}` | Get task by ID (owner only) |
| POST | `/api/tasks` | Create task (optionally linked to a project) |
| PUT | `/api/tasks/{id}` | Partial update (owner only) |
| DELETE | `/api/tasks/{id}` | Delete task (owner only) |

### Projects (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/projects` | List authenticated user's projects |
| POST | `/api/projects` | Create a new project |
| GET | `/api/projects/{id}/tasks` | List tasks in a project (owner only) |

### Example flows

**Register and get a token:**
```bash
POST /api/auth/register
Content-Type: application/json

{ "username": "alice", "email": "alice@example.com", "password": "secret" }
```
```json
{ "token": "<jwt>" }
```

**Create a task:**
```bash
POST /api/tasks
Authorization: Bearer <jwt>
Content-Type: application/json

{ "title": "Learn Spring Boot", "description": "Build a REST API", "status": "TODO" }
```
```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "Build a REST API",
  "status": "TODO",
  "priority": null,
  "dueDate": null,
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-01-15T10:30:00",
  "ownerUsername": "alice"
}
```

**Create a task linked to a project:**
```bash
POST /api/tasks
Authorization: Bearer <jwt>
Content-Type: application/json

{ "title": "Set up CI", "status": "TODO", "projectId": 1 }
```

## 🏃 Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 15+ (or Docker)

### Option 1: Run with Docker (Recommended)
```bash
git clone https://github.com/EliBen8/taskmanager.git
cd taskmanager
docker-compose up
# API available at http://localhost:8080
```

### Option 2: Run Locally

**1. Start PostgreSQL:**
```bash
docker run --name postgres-taskdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=taskdb \
  -p 5432:5432 -d postgres:15
```

**2. Configure JWT secret in `src/main/resources/application.properties`:**
```properties
app.jwt.secret=your-secret-key-min-32-chars-long
app.jwt.expiration=3600
```

**3. Run the application:**
```bash
mvn spring-boot:run
```

Flyway will apply all pending migrations (V1–V5) automatically on startup.

## 🗄️ Database Migrations

Schema is managed exclusively by Flyway — `ddl-auto` is disabled.

| File | Description |
|------|-------------|
| `V1__create_tasks.sql` | Initial `tasks` table |
| `V2__add_priority.sql` | `priority` column on tasks |
| `V3__add_due_date.sql` | `due_date` column on tasks |
| `V4__add_user_id_to_tasks.sql` | `users`, `roles`, `user_roles` tables; `user_id` FK on tasks |
| `V5__create_projects.sql` | `projects` table; `project_id` FK on tasks |

## 🧪 Running Tests
```bash
mvn test
```

Tests use an H2 in-memory database (Flyway disabled, Hibernate `create-drop`). No running PostgreSQL instance is needed.

**Test coverage:**
- Repository layer: 6 tests (`TaskRepositoryTest`)
- Service layer: 11 tests (`TaskServiceTest`)
- Controller layer: 8 tests (`TaskControllerTest`)
- **Total: 25 tests**

## 🏗️ Project Structure

```
taskmanager/
├── src/
│   ├── main/
│   │   ├── java/com/taskapi/taskmanager/
│   │   │   ├── config/         # SecurityConfig (JWT, stateless, CORS)
│   │   │   ├── controller/     # TaskController, AuthController, ProjectController
│   │   │   ├── dto/            # Request/Response records (TaskResponse, CreateTaskRequest, …)
│   │   │   ├── entity/         # JPA entities (Task, User, Role, Project, TaskStatus)
│   │   │   ├── exception/      # Custom exceptions + GlobalExceptionHandler
│   │   │   ├── mapper/         # MapStruct interfaces (TaskMapper, ProjectMapper)
│   │   │   ├── repository/     # Spring Data JPA repositories
│   │   │   ├── security/       # JwtUtil, JwtFilter, UserDetailsServiceImpl
│   │   │   ├── service/        # TaskService, UserService, ProjectService
│   │   │   └── TaskmanagerApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/   # Flyway SQL scripts (V1–V5)
│   └── test/
│       ├── java/com/taskapi/taskmanager/
│       │   ├── controller/     # TaskControllerTest (@WebMvcTest)
│       │   ├── service/        # TaskServiceTest (Mockito)
│       │   └── repository/     # TaskRepositoryTest (@DataJpaTest)
│       └── resources/
│           └── application.properties  # H2 config for tests
├── .github/workflows/ci.yml    # GitHub Actions CI
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## 🔐 Security

- Stateless sessions (`SessionCreationPolicy.STATELESS`) — no cookies or server-side sessions
- All endpoints under `/api/tasks` and `/api/projects` require a valid JWT
- JWT signed with HMAC-SHA256; configurable secret and expiry (`app.jwt.secret`, `app.jwt.expiration`)
- Passwords hashed with BCrypt
- Cross-user access returns HTTP 403; missing/invalid JWT returns HTTP 401
- `GlobalExceptionHandler` never exposes stack traces or class names in responses

## ⚠️ Error Responses

All errors return a consistent JSON envelope:

```json
{ "timestamp": "2025-01-15T10:30:00Z", "status": 404, "error": "Not Found", "message": "Task not found with id: 42" }
```

Validation errors (HTTP 400) return a field-level array:

```json
[{ "field": "title", "message": "must not be blank" }]
```

## 🔄 CI/CD

GitHub Actions runs on every push: compiles, executes the full test suite, and reports results.

## 🐳 Docker

```bash
# Build
docker build -t taskmanager-app .

# Run with Docker Compose (app + PostgreSQL)
docker-compose up -d

# Logs
docker-compose logs -f

# Stop
docker-compose down
```

## 📝 Configuration reference

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/taskdb
spring.datasource.username=postgres
spring.datasource.password=password

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# JWT
app.jwt.secret=your-secret-key-min-32-chars-long-for-hs256
app.jwt.expiration=3600

# Server
server.port=8080
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

## 👤 Author

**Pablo Jahnen Mellado**
- GitHub: [@PabloJM21](https://github.com/PabloJM21)
- LinkedIn: [Your LinkedIn](https://www.linkedin.com/in/pablo-jahnen-mellado-b4a76020a)
