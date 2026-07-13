# Design Document: TaskFlow Architecture Upgrade

## Overview

This design describes the comprehensive refactoring of the Spring Boot task manager application
(`com.taskapi.taskmanager`). The current codebase is a minimal flat-layered application with four
packages (`controller`, `model`, `repository`, `service`), a single `Task` entity using a boolean
`completed` field, and no authentication, validation infrastructure, or schema management.

The upgrade evolves the system in nine incremental steps:

1. Feature-oriented package structure
2. `TaskStatus` enum replacing `boolean completed`
3. DTOs with MapStruct mappers
4. Global exception handling via `@RestControllerAdvice`
5. Bean Validation on request DTOs
6. Flyway database migrations (V1–V5)
7. JWT-based stateless authentication with Spring Security
8. Task ownership tied to authenticated users
9. `Project` entity for grouping tasks

Each step is designed to be merged independently. Public API contracts (endpoint paths, request /
response shapes, HTTP status codes) must not regress during the migration.

---

## Architecture

The system is a single Spring Boot module (`src/`) built with Maven, targeting Java 17 and
PostgreSQL. After the upgrade the layering is:

```
HTTP Client
    │
    ▼
┌─────────────────────────────────────────────────┐
│  Spring Security Filter Chain                   │
│   └── JwtFilter (extracts + validates JWT)      │
└──────────────┬──────────────────────────────────┘
               │
    ▼
┌─────────────────────────────────────────────────┐
│  Controllers  (REST endpoints, DTO binding)     │
│   TaskController  AuthController  ProjectController │
└──────────────┬──────────────────────────────────┘
               │ DTOs
    ▼
┌─────────────────────────────────────────────────┐
│  Services  (business logic, ownership checks)   │
│   TaskService  UserService  ProjectService      │
└──────────────┬──────────────────────────────────┘
               │ entities
    ▼
┌─────────────────────────────────────────────────┐
│  Repositories  (Spring Data JPA)                │
│   TaskRepository  UserRepository  ProjectRepository │
└──────────────┬──────────────────────────────────┘
               │
    ▼
┌─────────────────────────────────────────────────┐
│  PostgreSQL  (schema managed by Flyway)         │
└─────────────────────────────────────────────────┘
```

Cross-cutting concerns:
- **GlobalExceptionHandler** (`@RestControllerAdvice`) — intercepts all exceptions before they
  reach the client.
- **MapStruct mappers** — pure compile-time mapping between entities and DTOs, no runtime
  reflection.
- **Bean Validation** — `@Valid` on controller method parameters; violations surface as
  `MethodArgumentNotValidException` caught by the global handler.

---

## Components and Interfaces

### Package Structure

After the upgrade the base package `com.taskapi.taskmanager` is organised as follows:

```
com.taskapi.taskmanager/
├── config/
│   ├── SecurityConfig.java          # Spring Security + JWT bean wiring
│   └── FlywayConfig.java            # (optional) custom Flyway callbacks
├── controller/
│   ├── TaskController.java
│   ├── AuthController.java
│   └── ProjectController.java
├── dto/
│   ├── TaskResponse.java
│   ├── CreateTaskRequest.java
│   ├── UpdateTaskRequest.java
│   ├── ProjectResponse.java
│   ├── CreateProjectRequest.java
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   └── AuthResponse.java            # { token: String }
├── entity/
│   ├── Task.java
│   ├── User.java
│   ├── Role.java
│   └── Project.java
├── exception/
│   ├── TaskNotFoundException.java
│   ├── ProjectNotFoundException.java
│   └── GlobalExceptionHandler.java
├── mapper/
│   ├── TaskMapper.java              # MapStruct interface
│   └── ProjectMapper.java
├── repository/
│   ├── TaskRepository.java
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   └── ProjectRepository.java
├── security/
│   ├── JwtFilter.java               # OncePerRequestFilter
│   ├── JwtUtil.java                 # sign / validate / extract claims
│   └── UserDetailsServiceImpl.java
├── service/
│   ├── TaskService.java
│   ├── UserService.java
│   └── ProjectService.java
└── TaskmanagerApplication.java
```

### REST API Surface

| Method | Path | Auth required | Description |
|--------|------|--------------|-------------|
| POST | `/api/auth/register` | No | Create user, return JWT |
| POST | `/api/auth/login` | No | Authenticate, return JWT |
| GET | `/api/tasks` | Yes | List authenticated user's tasks |
| GET | `/api/tasks/{id}` | Yes | Get single task (owner only) |
| POST | `/api/tasks` | Yes | Create task |
| PUT | `/api/tasks/{id}` | Yes | Update task (owner only) |
| DELETE | `/api/tasks/{id}` | Yes | Delete task (owner only) |
| GET | `/api/projects` | Yes | List authenticated user's projects |
| POST | `/api/projects` | Yes | Create project |
| GET | `/api/projects/{id}/tasks` | Yes | List tasks in project (owner only) |

### GlobalExceptionHandler

Handles the following exception types and returns the documented JSON shape:

| Exception | HTTP Status | Response shape |
|-----------|------------|----------------|
| `TaskNotFoundException` | 404 | `{ timestamp, status, error, message }` |
| `ProjectNotFoundException` | 404 | `{ timestamp, status, error, message }` |
| `MethodArgumentNotValidException` | 400 | `[{ field, message }, …]` |
| `AccessDeniedException` | 403 | `{ timestamp, status, error, message }` |
| `UsernameNotFoundException` | 401 | `{ timestamp, status, error, message }` |
| `DuplicateUserException` | 409 | `{ timestamp, status, error, message }` |
| `Exception` (fallback) | 500 | `{ timestamp, status, error, message }` — **no** stack trace |

The 500 handler MUST NOT include class names, stack trace lines, or implementation identifiers.

---

## Data Models

### TaskStatus Enum

```java
public enum TaskStatus {
    TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED
}
```

Stored with `@Enumerated(EnumType.STRING)` so the column holds the literal name (e.g.
`"IN_PROGRESS"`). Default value on entity creation is `TaskStatus.TODO`.

### Task Entity

```java
@Entity @Table(name = "tasks")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 255)
    @Column(nullable = false)
    private String title;

    @Size(max = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Column(length = 50)
    private String priority;          // e.g. LOW, MEDIUM, HIGH — free text initially

    private LocalDate dueDate;

    @CreationTimestamp @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;          // optional
}
```

### User Entity

```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;          // BCrypt hash

    @Column(unique = true, nullable = false)
    private String email;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
```

### Role Entity

```java
@Entity @Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;    // e.g. ROLE_USER, ROLE_ADMIN
}
```

### Project Entity

```java
@Entity @Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @Size(max = 500)
    private String description;

    @CreationTimestamp @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
```

### DTOs

**TaskResponse** (9 required fields):
```java
public record TaskResponse(
    Long id, String title, String description,
    TaskStatus status, String priority,
    LocalDate dueDate, LocalDateTime createdAt,
    LocalDateTime updatedAt, String ownerUsername
) {}
```

**CreateTaskRequest**:
```java
public record CreateTaskRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 1000) String description,
    @NotNull TaskStatus status,
    String priority,
    LocalDate dueDate,
    Long projectId
) {}
```

**UpdateTaskRequest** — all fields optional (null means "leave unchanged"):
```java
public record UpdateTaskRequest(
    @Size(max = 255) String title,
    @Size(max = 1000) String description,
    TaskStatus status,
    String priority,
    LocalDate dueDate,
    Long projectId
) {}
```

**ProjectResponse**:
```java
public record ProjectResponse(
    Long id, String name, String description,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}
```

**CreateProjectRequest**:
```java
public record CreateProjectRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description
) {}
```

**RegisterRequest** / **LoginRequest** / **AuthResponse**:
```java
public record RegisterRequest(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank String password
) {}

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

public record AuthResponse(String token) {}
```

### MapStruct Mappers

```java
@Mapper(componentModel = "spring")
public interface TaskMapper {
    @Mapping(source = "owner.username", target = "ownerUsername")
    TaskResponse toTaskResponse(Task task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toTask(CreateTaskRequest request);
}
```

```java
@Mapper(componentModel = "spring")
public interface ProjectMapper {
    ProjectResponse toProjectResponse(Project project);
}
```

### Flyway Migration Scripts

| File | Action |
|------|--------|
| `V1__create_tasks.sql` | Create `tasks` table with `id`, `title`, `description`, `status`, `created_at`, `updated_at` |
| `V2__add_priority.sql` | `ALTER TABLE tasks ADD COLUMN priority VARCHAR(50)` |
| `V3__add_due_date.sql` | `ALTER TABLE tasks ADD COLUMN due_date DATE` |
| `V4__add_user_id_to_tasks.sql` | Create `roles`, `users`, `user_roles` tables; add `user_id` FK to `tasks` |
| `V5__create_projects.sql` | Create `projects` table; add `project_id` FK to `tasks` |

All scripts live in `src/main/resources/db/migration/`. The property
`spring.jpa.hibernate.ddl-auto` is removed and replaced with:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

### JWT Security

- Library: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (0.12.x).
- Algorithm: HMAC-SHA256 with a secret key configured via `app.jwt.secret` property.
- Expiry: 3600 seconds (configurable via `app.jwt.expiration`).
- `JwtFilter` extends `OncePerRequestFilter`. It:
  1. Reads `Authorization: Bearer <token>` header.
  2. Validates signature and expiry.
  3. Loads `UserDetails` and sets `UsernamePasswordAuthenticationToken` in `SecurityContext`.
  4. On any validation failure, clears the context and lets Spring Security return 401.
- `/api/auth/**` endpoints are publicly accessible; all other endpoints require authentication.

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a
system — essentially, a formal statement about what the system should do. Properties serve as the
bridge between human-readable specifications and machine-verifiable correctness guarantees.*

**Property Reflection** — Before listing final properties, redundancies were eliminated:
- Requirements 3.9 (field preservation in mapping) is fully covered by Property P7 (full mapping
  fidelity), so 3.9 generates no separate property.
- Requirements 3.10 and 5.1 both cover blank-title rejection; they are merged into P9.
- Requirements 4.7 (no stack traces) is subsumed by Property P11 (unhandled exception response).
- Requirements 2.3 (default status TODO) is combined with the default-value mapping covered by P7;
  a dedicated property P3 is retained because it tests runtime behaviour independently of the
  mapper.

---

### Property 1: TaskStatus JSON round-trip

*For any* `TaskStatus` value, serializing it to JSON and then deserializing the resulting JSON
string back to a `TaskStatus` must yield a value equal to the original.

**Validates: Requirements 2.6**

---

### Property 2: TaskStatus database round-trip

*For any* `TaskStatus` value, persisting a `Task` with that status and then loading the same row
from the database must yield a `Task` whose `status` field equals the original value.

**Validates: Requirements 2.5**

---

### Property 3: Default status on creation

*For any* `CreateTaskRequest` that does not set an explicit status value (status field is omitted
or null at the point the entity is first saved), the created `Task` persisted in the database must
have `status == TaskStatus.TODO`.

**Validates: Requirements 2.3**

---

### Property 4: Invalid status produces HTTP 400 with context

*For any* string that is not a member of `{TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED}`, submitting
that string as the `status` field in a task creation or update request must produce an HTTP 400
response whose body contains both the submitted invalid value and the set of accepted enum names.

**Validates: Requirements 2.4**

---

### Property 5: Task-to-TaskResponse mapping fidelity

*For any* `Task` entity with arbitrary field values, invoking the `TaskMapper.toTaskResponse`
mapper must produce a `TaskResponse` whose nine fields (`id`, `title`, `description`, `status`,
`priority`, `dueDate`, `createdAt`, `updatedAt`, `ownerUsername`) equal the corresponding field
values of the source entity (with `ownerUsername` taken from `task.getOwner().getUsername()`).

**Validates: Requirements 3.7, 3.9**

---

### Property 6: CreateTaskRequest-to-Task mapping fidelity

*For any* valid `CreateTaskRequest`, invoking `TaskMapper.toTask` must produce a `Task` whose
`title`, `description`, `status`, `priority`, and `dueDate` fields equal the corresponding fields
of the source request.

**Validates: Requirements 3.8**

---

### Property 7: GET /api/tasks/{id} response completeness

*For any* `Task` stored in the database, `GET /api/tasks/{id}` (authenticated as the owner) must
return a JSON body that contains all nine required `TaskResponse` fields (`id`, `title`,
`description`, `status`, `priority`, `dueDate`, `createdAt`, `updatedAt`, `ownerUsername`).

**Validates: Requirements 3.1, 3.6**

---

### Property 8: Partial update leaves unspecified fields unchanged

*For any* `Task` stored in the database and *for any* `UpdateTaskRequest` that sets only a strict
subset of the updatable fields (`title`, `description`, `status`, `priority`, `dueDate`), the
fields absent from the request must have the same values after the update as they had before.

**Validates: Requirements 3.3**

---

### Property 9: Blank title is rejected with field name in response

*For any* string composed entirely of whitespace characters (including the empty string), submitting
that string as the `title` field in a `CreateTaskRequest` must produce an HTTP 400 response whose
body contains the field name `"title"`.

**Validates: Requirements 3.10, 5.1**

---

### Property 10: Title length boundary

*For any* string of length greater than 255, submitting it as the `title` field in a
`CreateTaskRequest` must produce an HTTP 400 response whose body contains the field name `"title"`.
*For any* string of length between 1 and 255 (inclusive) with a non-null `status`, the request
must not be rejected due to `title` or `status` validation.

**Validates: Requirements 5.2, 5.4**

---

### Property 11: Unhandled exception yields safe HTTP 500

*For any* unhandled `Exception` thrown during request processing, the `GlobalExceptionHandler`
must return HTTP 500 with a response body that contains none of the following: Java class names
(patterns matching `[A-Za-z]+(\.[A-Za-z]+)+Exception`), stack trace lines (patterns matching
`at [A-Za-z]`), or heap addresses (patterns matching `@[0-9a-f]+`).

**Validates: Requirements 4.6, 4.7**

---

### Property 12: TaskNotFoundException yields well-formed HTTP 404

*For any* task ID that is not present in the database, `GET /api/tasks/{id}` (authenticated as
any user) must return HTTP 404 with a JSON body containing all four fields: `timestamp`, `status`,
`error`, and `message`.

**Validates: Requirements 4.3, 4.4**

---

### Property 13: Validation error response structure

*For any* `CreateTaskRequest` that fails Bean Validation on one or more fields, the HTTP 400
response body must be a JSON array where every element contains both a `field` property and a
`message` property.

**Validates: Requirements 4.5**

---

### Property 14: Invalid email is rejected with field name

*For any* string that does not match the `local-part@domain` format (where `local-part` is at
least one character and `domain` contains at least one dot), submitting it as the `email` field in
a registration request must produce an HTTP 400 response whose body contains the field name
`"email"`.

**Validates: Requirements 5.5, 5.6**

---

### Property 15: Registration returns a JWT

*For any* valid registration request with a username and email not already present in the system,
`POST /api/auth/register` must return a non-empty JWT string in the response body.

**Validates: Requirements 7.4**

---

### Property 16: Login returns a JWT with correct expiry

*For any* registered user and their correct password, `POST /api/auth/login` must return a JWT
whose `exp` claim equals the `iat` claim plus 3600 seconds (± 5 seconds clock tolerance).

**Validates: Requirements 7.5**

---

### Property 17: Invalid JWT is rejected with HTTP 401

*For any* string that is not a structurally valid, correctly-signed, non-expired JWT, presenting it
in the `Authorization: Bearer` header on a protected endpoint must produce HTTP 401.

**Validates: Requirements 7.6**

---

### Property 18: Protected endpoints reject unauthenticated requests

*For any* `TaskController` endpoint (any HTTP method, any path under `/api/tasks`), a request with
no `Authorization` header must produce HTTP 401.

**Validates: Requirements 7.9**

---

### Property 19: ROLE_USER cannot access admin endpoints

*For any* authenticated user whose roles contain only `ROLE_USER`, accessing any endpoint
designated as requiring `ROLE_ADMIN` must produce HTTP 403.

**Validates: Requirements 7.11**

---

### Property 20: Duplicate username or email yields HTTP 409

*For any* username or email already present in the `users` table, a `POST /api/auth/register`
request using that username or email must return HTTP 409.

**Validates: Requirements 7.12**

---

### Property 21: Task creation associates with authenticated user

*For any* authenticated user `U` who creates a task via `POST /api/tasks`, the persisted `Task`
must have its `owner` field equal to `U`.

**Validates: Requirements 8.2**

---

### Property 22: Task list contains only owner's tasks

*For any* two distinct users `U1` and `U2`, `GET /api/tasks` authenticated as `U1` must return
only tasks whose `owner` is `U1` — the result must contain no tasks owned by `U2`.

**Validates: Requirements 8.3**

---

### Property 23: Cross-user task access yields HTTP 403

*For any* user `U1` and *for any* task `T` owned by a different user `U2`, any of `GET`,
`PUT`, or `DELETE` on `/api/tasks/{T.id}` by `U1` must return HTTP 403.

**Validates: Requirements 8.4**

---

### Property 24: POST /api/projects response contains all required fields

*For any* valid `CreateProjectRequest` with a non-blank name, `POST /api/projects` (authenticated)
must return a response body containing `id`, `name`, `description`, `createdAt`, and `updatedAt`
with `name` and `description` values equal to those in the request.

**Validates: Requirements 9.3**

---

### Property 25: Project list contains only owner's projects

*For any* two distinct users `U1` and `U2`, `GET /api/projects` authenticated as `U1` must return
only projects owned by `U1` — the result must contain no projects owned by `U2`.

**Validates: Requirements 9.4**

---

### Property 26: Task projectId association round-trip

*For any* task created with a valid `projectId` (referencing a project owned by the authenticated
user), the task returned by a subsequent `GET /api/tasks/{id}` must reference the same project.

**Validates: Requirements 9.6**

---

### Property 27: Cross-user project task assignment yields HTTP 403

*For any* user `U1` and *for any* project `P` owned by a different user `U2`, creating a task with
`projectId = P.id` while authenticated as `U1` must return HTTP 403.

**Validates: Requirements 9.8**

---

### Property 28: Non-existent projectId yields HTTP 404 with all error fields

*For any* `projectId` value that does not correspond to an existing project, a task creation
request referencing that `projectId` must return HTTP 404 with a JSON body containing all four
fields: `timestamp`, `status`, `error`, and `message`.

**Validates: Requirements 9.9**

---

### Property 29: Blank project name is rejected with field name in response

*For any* string composed entirely of whitespace (or null) submitted as the `name` field in a
`CreateProjectRequest`, `POST /api/projects` must return HTTP 400 with a body containing the
field name `"name"`.

**Validates: Requirements 9.11**

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── TaskNotFoundException            (→ 404)
├── ProjectNotFoundException         (→ 404)
└── DuplicateUserException           (→ 409)
```

Spring Security exceptions (`AccessDeniedException`, `AuthenticationException`) are handled by
`SecurityConfig`'s `authenticationEntryPoint` and `accessDeniedHandler` beans, which delegate to
the same JSON error shape as the `GlobalExceptionHandler`.

### Error Response Shape

All error responses use a consistent envelope:

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Task not found with id: 42"
}
```

Validation error responses (HTTP 400 from Bean Validation) use a different shape to surface
per-field detail:

```json
[
  { "field": "title",  "message": "must not be blank" },
  { "field": "status", "message": "must not be null"  }
]
```

### Safety Invariants

- The generic fallback handler for `Exception` logs the full stack trace server-side but returns
  only a generic message to the client.
- No handler method may call `e.getMessage()` on an arbitrary `Exception` in the 500 branch —
  only a static message is safe.
- JWT validation errors are handled inside `JwtFilter` before they reach the dispatcher; they
  never propagate to `GlobalExceptionHandler`.

---

## Testing Strategy

### Property-Based Testing Library

All property-based tests use **jqwik** (`net.jqwik:jqwik:1.9.x`) with a minimum of **100
iterations** per test. Each test is tagged with a comment referencing the design property it
validates, using the format:
`// Feature: taskflow-architecture-upgrade, Property <N>: <property_text>`

### Unit Tests (example-based)

Focus on concrete scenarios that are not covered by properties:

- `TaskStatus` enum value existence (exactly 5 members).
- `User`/`Role`/`Project` entity structural assertions.
- `GlobalExceptionHandler` for `TaskNotFoundException` → verify all 4 JSON fields present.
- Expired JWT → HTTP 401 with expiry message.
- Login with wrong credentials → HTTP 401.
- `SecurityConfig` session policy is STATELESS.
- Flyway migration file existence (`V1`–`V5`).

### Property Tests (jqwik)

Each Correctness Property above maps to one jqwik `@Property` test:

| Property | Test class | Generators |
|----------|-----------|-----------|
| P1 (JSON round-trip) | `TaskStatusSerializationTest` | `@ForAll TaskStatus` |
| P2 (DB round-trip) | `TaskStatusPersistenceTest` | `@ForAll TaskStatus` (with `@DataJpaTest`) |
| P3 (default TODO) | `TaskDefaultStatusTest` | `@ForAll` valid task title/description |
| P4 (invalid status → 400) | `TaskStatusValidationTest` | `@ForAll @StringNotIn("TODO","IN_PROGRESS","REVIEW","DONE","CANCELLED") String` |
| P5 (mapper fidelity Task→DTO) | `TaskMapperTest` | `@ForAll` Task via `TaskArbitrary` |
| P6 (mapper fidelity DTO→Task) | `TaskMapperTest` | `@ForAll` valid `CreateTaskRequest` |
| P7 (GET response completeness) | `TaskControllerPropertyTest` | `@ForAll` Task |
| P8 (partial update) | `TaskServicePropertyTest` | `@ForAll Task, @ForAll UpdateTaskRequest` |
| P9 (blank title → 400) | `TaskValidationTest` | `@ForAll @Whitespace String` |
| P10 (title length boundary) | `TaskValidationTest` | `@ForAll @StringLength(min=256)` and `@ForAll @StringLength(max=255)` |
| P11 (safe 500) | `GlobalExceptionHandlerTest` | `@ForAll RuntimeException` |
| P12 (404 error shape) | `TaskNotFoundTest` | `@ForAll @Positive Long` (not seeded) |
| P13 (validation array shape) | `ValidationErrorShapeTest` | `@ForAll` invalid request |
| P14 (invalid email → 400) | `UserValidationTest` | `@ForAll` non-email strings |
| P15 (register → JWT) | `AuthControllerPropertyTest` | `@ForAll` valid username/email/password |
| P16 (login JWT expiry) | `AuthControllerPropertyTest` | `@ForAll` registered users |
| P17 (invalid JWT → 401) | `JwtFilterPropertyTest` | `@ForAll` arbitrary/malformed token strings |
| P18 (unauthenticated → 401) | `TaskControllerPropertyTest` | `@ForAll` endpoint paths |
| P19 (ROLE_USER 403 admin) | `AuthorizationPropertyTest` | `@ForAll` ROLE_USER accounts |
| P20 (duplicate → 409) | `AuthControllerPropertyTest` | `@ForAll` existing username/email |
| P21 (owner on creation) | `TaskOwnershipTest` | `@ForAll` user + task payload |
| P22 (task list isolation) | `TaskOwnershipTest` | `@ForAll` two distinct users |
| P23 (cross-user 403) | `TaskOwnershipTest` | `@ForAll` user pair + task |
| P24 (project response fields) | `ProjectControllerPropertyTest` | `@ForAll` valid project request |
| P25 (project list isolation) | `ProjectOwnershipTest` | `@ForAll` two distinct users |
| P26 (projectId round-trip) | `TaskProjectAssociationTest` | `@ForAll` user + project + task |
| P27 (cross-user projectId 403) | `ProjectOwnershipTest` | `@ForAll` user pair + project |
| P28 (missing project 404) | `ProjectNotFoundTest` | `@ForAll @Positive Long` (not seeded) |
| P29 (blank project name → 400) | `ProjectValidationTest` | `@ForAll @Whitespace String` |

### Integration Tests

Cover Flyway and Spring Security wiring that cannot be validated with pure unit/property tests:

- Application starts from a clean database and all 5 migrations apply in order.
- Application starts a second time against an already-migrated database without error.
- A modified migration script causes startup failure with a descriptive Flyway error.
- Full authentication flow (register → login → access protected endpoint) over real HTTP.

### Test Infrastructure

- **H2 in-memory** (PostgreSQL-compatible mode) for `@DataJpaTest` and property tests that touch
  the database.
- **`@SpringBootTest` + `TestRestTemplate`** for integration/controller tests.
- **`@WebMvcTest`** + MockMvc for controller slice tests (no full context).
- Existing tests in `TaskControllerTest`, `TaskServiceTest`, and `TaskRepositoryTest` must be
  updated to use the new DTO types and the `TaskStatus` enum; their assertions on `completed`
  field are replaced with assertions on `status`.
