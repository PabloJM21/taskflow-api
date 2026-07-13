# Implementation Plan: TaskFlow Architecture Upgrade

## Overview

This plan implements the nine incremental upgrade steps for `com.taskapi.taskmanager`, evolving a flat-layered Spring Boot application into a feature-oriented, fully-authenticated, schema-managed task manager. Each task builds on the previous, wiring all components together at the end of each step. No public API contracts regress at any point.

---

## Tasks

- [x] 1. Reorganise package structure and update build
  - [x] 1.1 Create new package directories and move existing classes
    - Create `config/`, `controller/`, `dto/`, `entity/`, `exception/`, `mapper/`, `repository/`, `security/`, `service/`, `validation/` under `com.taskapi.taskmanager`
    - Move `Task.java` → `entity/`, `TaskController.java` → `controller/`, `TaskService.java` → `service/`, `TaskRepository.java` → `repository/`
    - Fix all import statements; confirm the application compiles with `mvn compile`
    - _Requirements: 1.1, 1.3_
  - [x] 1.2 Add MapStruct and jqwik dependencies to `pom.xml`
    - Add `org.mapstruct:mapstruct` and `org.mapstruct:mapstruct-processor` (annotation processor) at a stable 1.5.x version
    - Add `net.jqwik:jqwik:1.9.x` in the `test` scope
    - Add `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.x) dependencies
    - Add `org.flywaydb:flyway-core` dependency
    - _Requirements: 1.1, 6.1_

- [x] 2. Introduce `TaskStatus` enum and update `Task` entity
  - [x] 2.1 Define `TaskStatus` enum and replace `boolean completed`
    - Create `entity/TaskStatus.java` with values `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`, `CANCELLED`
    - Replace `boolean completed` with `@Enumerated(EnumType.STRING) TaskStatus status` defaulting to `TaskStatus.TODO` in `Task.java`
    - Update `TaskService` and `TaskController` to use `status`; remove all references to `completed`
    - Update existing `TaskControllerTest`, `TaskServiceTest`, `TaskRepositoryTest` to assert on `status` instead of `completed`
    - _Requirements: 2.1, 2.2, 2.3, 2.5_
  - [ ]* 2.2 Write property test P1 — TaskStatus JSON round-trip
    - **Property 1: TaskStatus JSON round-trip**
    - **Validates: Requirements 2.6**
    - Create `TaskStatusSerializationTest` using `@ForAll TaskStatus`; serialize via Jackson `ObjectMapper`, deserialize back, assert equality
  - [ ]* 2.3 Write property test P2 — TaskStatus database round-trip
    - **Property 2: TaskStatus database round-trip**
    - **Validates: Requirements 2.5**
    - Create `TaskStatusPersistenceTest` with `@DataJpaTest`; persist a `Task` for each `@ForAll TaskStatus`, reload by ID, assert `status` equals original
  - [ ]* 2.4 Write property test P3 — Default status TODO on creation
    - **Property 3: Default status on creation**
    - **Validates: Requirements 2.3**
    - Create `TaskDefaultStatusTest`; generate arbitrary valid title/description, create and persist entity without setting status, assert `status == TaskStatus.TODO`

- [ ] 3. Introduce DTOs, MapStruct mappers, and update controller
  - [x] 3.1 Define DTO records
    - Create `dto/TaskResponse.java` (9 fields: `id`, `title`, `description`, `status`, `priority`, `dueDate`, `createdAt`, `updatedAt`, `ownerUsername`)
    - Create `dto/CreateTaskRequest.java` with `@NotBlank @Size(max=255) String title`, `@Size(max=1000) String description`, `@NotNull TaskStatus status`, optional `priority`, `dueDate`, `projectId`
    - Create `dto/UpdateTaskRequest.java` with all fields optional
    - _Requirements: 3.1, 3.2, 3.3_
  - [-] 3.2 Implement `TaskMapper` with MapStruct
    - Create `mapper/TaskMapper.java` as a `@Mapper(componentModel = "spring")` interface
    - Map `owner.username` → `ownerUsername` in `toTaskResponse`; ignore `id`, `owner`, `project`, `createdAt`, `updatedAt` in `toTask`
    - _Requirements: 3.7, 3.8_
  - [ ] 3.3 Update `TaskController` to use DTOs
    - Inject `TaskMapper`; replace entity parameters/returns with `CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse`
    - Add `@Valid` to request body parameters
    - Update `TaskService` to accept/return DTOs or delegate mapping appropriately
    - Update existing tests to use new DTO types
    - _Requirements: 3.4, 3.5, 3.6_
  - [ ]* 3.4 Write property test P5 — Task→TaskResponse mapper fidelity
    - **Property 5: Task-to-TaskResponse mapping fidelity**
    - **Validates: Requirements 3.7, 3.9**
    - Create `TaskMapperTest`; generate arbitrary `Task` entities via `TaskArbitrary`, invoke `toTaskResponse`, assert all 9 fields match
  - [ ]* 3.5 Write property test P6 — CreateTaskRequest→Task mapper fidelity
    - **Property 6: CreateTaskRequest-to-Task mapping fidelity**
    - **Validates: Requirements 3.8**
    - In `TaskMapperTest`; generate valid `CreateTaskRequest`, invoke `toTask`, assert `title`, `description`, `status`, `priority`, `dueDate` match

- [ ] 4. Implement `GlobalExceptionHandler` and custom exceptions
  - [~] 4.1 Create custom exception classes
    - Create `exception/TaskNotFoundException.java` extending `RuntimeException`
    - Create `exception/ProjectNotFoundException.java` extending `RuntimeException`
    - Create `exception/DuplicateUserException.java` extending `RuntimeException`
    - Update `TaskService` to throw `TaskNotFoundException` instead of returning `null`
    - _Requirements: 4.2, 4.3_
  - [~] 4.2 Implement `GlobalExceptionHandler`
    - Create `exception/GlobalExceptionHandler.java` annotated with `@RestControllerAdvice`
    - Handle `TaskNotFoundException` → HTTP 404 with `{ timestamp, status, error, message }` body
    - Handle `ProjectNotFoundException` → HTTP 404 with same shape
    - Handle `MethodArgumentNotValidException` → HTTP 400 with `[{ field, message }, …]` array
    - Handle `DuplicateUserException` → HTTP 409
    - Handle `AccessDeniedException` → HTTP 403
    - Handle `Exception` (fallback) → HTTP 500 with static message, no stack trace, no class names
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 4.7_
  - [ ]* 4.3 Write property test P11 — Safe HTTP 500 response
    - **Property 11: Unhandled exception yields safe HTTP 500**
    - **Validates: Requirements 4.6, 4.7**
    - Create `GlobalExceptionHandlerTest`; generate arbitrary `RuntimeException` values via `@ForAll`, invoke fallback handler, assert response body contains no class names, stack trace lines, or heap addresses
  - [ ]* 4.4 Write property test P12 — TaskNotFoundException yields HTTP 404 with all fields
    - **Property 12: TaskNotFoundException yields well-formed HTTP 404**
    - **Validates: Requirements 4.3, 4.4**
    - Create `TaskNotFoundTest`; generate arbitrary `@Positive Long` IDs not seeded in DB, perform `GET /api/tasks/{id}`, assert HTTP 404 and JSON body has `timestamp`, `status`, `error`, `message`
  - [ ]* 4.5 Write property test P13 — Validation error response structure
    - **Property 13: Validation error response structure**
    - **Validates: Requirements 4.5**
    - Create `ValidationErrorShapeTest`; generate invalid `CreateTaskRequest` variations, assert 400 response body is a JSON array with every element containing `field` and `message`

- [ ] 5. Add Bean Validation to request DTOs and test boundary behaviour
  - [~] 5.1 Verify validation annotations are in place and wired
    - Confirm `@Valid` is present on all `@RequestBody` parameters in `TaskController` and future `AuthController`, `ProjectController`
    - Ensure `spring-boot-starter-validation` is on the classpath (add to `pom.xml` if missing)
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [ ]* 5.2 Write property test P4 — Invalid TaskStatus produces HTTP 400
    - **Property 4: Invalid status produces HTTP 400 with context**
    - **Validates: Requirements 2.4**
    - Create `TaskStatusValidationTest`; generate strings not in `{TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED}`, submit as `status` in a create request, assert HTTP 400 and body contains both the invalid value and the accepted enum names
  - [ ]* 5.3 Write property test P9 — Blank title rejected with field name
    - **Property 9: Blank title is rejected with field name in response**
    - **Validates: Requirements 3.10, 5.1**
    - Create `TaskValidationTest`; generate whitespace-only strings via `@ForAll @Whitespace String`, submit as `title`, assert HTTP 400 body contains `"title"`
  - [ ]* 5.4 Write property test P10 — Title length boundary
    - **Property 10: Title length boundary**
    - **Validates: Requirements 5.2, 5.4**
    - In `TaskValidationTest`; generate `@StringLength(min=256)` strings → assert HTTP 400 with `"title"`; generate `@StringLength(min=1, max=255)` strings with valid status → assert no 400 for those fields

- [ ] 6. Configure Flyway and write migration scripts
  - [~] 6.1 Remove `ddl-auto` and configure Flyway
    - Remove `spring.jpa.hibernate.ddl-auto=update` from `application.properties`
    - Add `spring.flyway.enabled=true` and `spring.flyway.locations=classpath:db/migration`
    - Create directory `src/main/resources/db/migration/`
    - _Requirements: 6.1_
  - [~] 6.2 Write migrations V1–V3
    - Create `V1__create_tasks.sql`: `CREATE TABLE tasks (id BIGSERIAL PRIMARY KEY, title VARCHAR(255) NOT NULL, description VARCHAR(1000), status VARCHAR(50) NOT NULL DEFAULT 'TODO', created_at TIMESTAMP, updated_at TIMESTAMP)`
    - Create `V2__add_priority.sql`: `ALTER TABLE tasks ADD COLUMN priority VARCHAR(50)`
    - Create `V3__add_due_date.sql`: `ALTER TABLE tasks ADD COLUMN due_date DATE`
    - _Requirements: 6.2, 6.3, 6.4_
  - [ ]* 6.3 Write unit tests for Flyway migration file existence
    - Assert `V1__create_tasks.sql`, `V2__add_priority.sql`, `V3__add_due_date.sql` exist on the classpath under `db/migration/`
    - _Requirements: 6.2, 6.3, 6.4_

- [ ] 7. Implement JWT-based authentication with Spring Security
  - [~] 7.1 Define `User` and `Role` entities
    - Create `entity/User.java` with `id`, `username`, `password`, `email`, `Set<Role> roles`
    - Create `entity/Role.java` with `id`, `name`
    - Create `repository/UserRepository.java` and `repository/RoleRepository.java`
    - _Requirements: 7.2, 7.3_
  - [~] 7.2 Implement `JwtUtil` for token signing and validation
    - Create `security/JwtUtil.java` using `io.jsonwebtoken` HMAC-SHA256
    - Read secret from `app.jwt.secret` property; expiry from `app.jwt.expiration` (default 3600)
    - Implement `generateToken(username)`, `validateToken(token)`, `extractUsername(token)`, `extractExpiry(token)`
    - _Requirements: 7.5, 7.6, 7.7_
  - [~] 7.3 Implement `UserDetailsServiceImpl` and `JwtFilter`
    - Create `security/UserDetailsServiceImpl.java` loading user by username from `UserRepository`
    - Create `security/JwtFilter.java` extending `OncePerRequestFilter`; read `Authorization: Bearer` header, validate JWT, populate `SecurityContext`; on failure clear context (Spring Security returns 401)
    - _Requirements: 7.6, 7.7, 7.8_
  - [~] 7.4 Configure `SecurityConfig`
    - Create `config/SecurityConfig.java`; set `SessionCreationPolicy.STATELESS`
    - Permit `/api/auth/**` without authentication; require authentication for all other paths
    - Register `JwtFilter` before `UsernamePasswordAuthenticationFilter`
    - Wire `authenticationEntryPoint` and `accessDeniedHandler` to return the same JSON error envelope
    - _Requirements: 7.1, 7.9, 7.11_
  - [~] 7.5 Implement `AuthController` and `UserService`
    - Create `service/UserService.java` with `register(RegisterRequest)` and `login(LoginRequest)` methods
    - `register`: validate uniqueness, BCrypt-hash password, assign `ROLE_USER`, save, return JWT
    - `login`: authenticate credentials, return JWT on success or throw on failure
    - Create `controller/AuthController.java` with `POST /api/auth/register` and `POST /api/auth/login`
    - Create `dto/RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`
    - _Requirements: 7.4, 7.5, 7.10, 7.12_
  - [ ]* 7.6 Write property test P15 — Registration returns a JWT
    - **Property 15: Registration returns a JWT**
    - **Validates: Requirements 7.4**
    - Create `AuthControllerPropertyTest`; generate valid unique username/email/password, call `POST /api/auth/register`, assert non-empty JWT string in response body
  - [ ]* 7.7 Write property test P16 — Login JWT expiry is correct
    - **Property 16: Login returns a JWT with correct expiry**
    - **Validates: Requirements 7.5**
    - In `AuthControllerPropertyTest`; register then login with correct password, decode JWT, assert `exp == iat + 3600` (±5 seconds tolerance)
  - [ ]* 7.8 Write property test P17 — Invalid JWT rejected with HTTP 401
    - **Property 17: Invalid JWT is rejected with HTTP 401**
    - **Validates: Requirements 7.6**
    - Create `JwtFilterPropertyTest`; generate arbitrary and malformed token strings, present in `Authorization: Bearer` header on a protected endpoint, assert HTTP 401
  - [ ]* 7.9 Write property test P18 — Unauthenticated requests to task endpoints yield HTTP 401
    - **Property 18: Protected endpoints reject unauthenticated requests**
    - **Validates: Requirements 7.9**
    - In `TaskControllerPropertyTest`; generate requests to paths under `/api/tasks` with no `Authorization` header, assert HTTP 401
  - [ ]* 7.10 Write property test P19 — ROLE_USER cannot access admin endpoints
    - **Property 19: ROLE_USER cannot access admin endpoints**
    - **Validates: Requirements 7.11**
    - Create `AuthorizationPropertyTest`; generate ROLE_USER accounts, access admin-designated endpoints, assert HTTP 403
  - [ ]* 7.11 Write property test P20 — Duplicate username or email yields HTTP 409
    - **Property 20: Duplicate username or email yields HTTP 409**
    - **Validates: Requirements 7.12**
    - In `AuthControllerPropertyTest`; register a user, attempt to register again with the same username or email, assert HTTP 409
  - [ ]* 7.12 Write property test P14 — Invalid email rejected with field name
    - **Property 14: Invalid email is rejected with field name**
    - **Validates: Requirements 5.5, 5.6**
    - Create `UserValidationTest`; generate strings not matching `local-part@domain` format, submit as `email` in `RegisterRequest`, assert HTTP 400 body contains `"email"`

- [~] 8. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Implement task ownership tied to authenticated user
  - [~] 9.1 Add `owner` relationship to `Task` and write migration V4
    - Add `@ManyToOne User owner` field to `Task.java` with `@JoinColumn(name = "user_id", nullable = false)`
    - Create `V4__add_user_id_to_tasks.sql`: create `roles`, `users`, `user_roles` tables; add `user_id` FK column to `tasks`
    - _Requirements: 8.1, 8.5_
  - [~] 9.2 Enforce ownership in `TaskService`
    - On task creation, resolve the authenticated principal from `SecurityContextHolder` and set as `owner`
    - On list, filter by `owner == currentUser` (`TaskRepository` query: `findAllByOwner`)
    - On get/update/delete, load task, check `task.owner == currentUser`; throw `AccessDeniedException` (→ 403) if mismatch
    - If JWT subject does not match any `User`, return HTTP 401
    - _Requirements: 8.2, 8.3, 8.4, 8.6_
  - [~] 9.3 Update `TaskResponse` to include `ownerUsername` and update mapper
    - Confirm `ownerUsername` is correctly mapped from `task.owner.username` via `TaskMapper`
    - Update integration tests to assert `ownerUsername` in responses
    - _Requirements: 3.1, 3.7_
  - [ ]* 9.4 Write property test P7 — GET /api/tasks/{id} response completeness
    - **Property 7: GET /api/tasks/{id} response completeness**
    - **Validates: Requirements 3.1, 3.6**
    - In `TaskControllerPropertyTest`; persist arbitrary tasks owned by authenticated user, perform `GET /api/tasks/{id}`, assert JSON body contains all 9 `TaskResponse` fields
  - [ ]* 9.5 Write property test P8 — Partial update leaves unspecified fields unchanged
    - **Property 8: Partial update leaves unspecified fields unchanged**
    - **Validates: Requirements 3.3**
    - Create `TaskServicePropertyTest`; generate arbitrary `Task` and `UpdateTaskRequest` setting only a strict subset of fields, perform update, assert absent fields are unchanged
  - [ ]* 9.6 Write property test P21 — Task creation associates with authenticated user
    - **Property 21: Task creation associates with authenticated user**
    - **Validates: Requirements 8.2**
    - Create `TaskOwnershipTest`; generate arbitrary user and task payload, create task via `POST /api/tasks`, assert persisted `Task.owner` equals the authenticated user
  - [ ]* 9.7 Write property test P22 — Task list contains only owner's tasks
    - **Property 22: Task list contains only owner's tasks**
    - **Validates: Requirements 8.3**
    - In `TaskOwnershipTest`; create tasks for two distinct users, call `GET /api/tasks` as user U1, assert response contains only tasks owned by U1
  - [ ]* 9.8 Write property test P23 — Cross-user task access yields HTTP 403
    - **Property 23: Cross-user task access yields HTTP 403**
    - **Validates: Requirements 8.4**
    - In `TaskOwnershipTest`; generate user pair and task owned by U2, attempt GET/PUT/DELETE as U1, assert HTTP 403

- [ ] 10. Implement `Project` entity and project management endpoints
  - [~] 10.1 Define `Project` entity, DTOs, and migrations V5
    - Create `entity/Project.java` with `id`, `name`, `description`, `createdAt`, `updatedAt`, `User owner`
    - Add optional `@ManyToOne Project project` to `Task.java`
    - Create `dto/ProjectResponse.java`, `dto/CreateProjectRequest.java`
    - Create `V5__create_projects.sql`: create `projects` table; add `project_id` FK column to `tasks`
    - _Requirements: 9.1, 9.2, 9.10_
  - [~] 10.2 Implement `ProjectRepository`, `ProjectMapper`, `ProjectService`, `ProjectController`
    - Create `repository/ProjectRepository.java` with `findAllByOwner` query
    - Create `mapper/ProjectMapper.java` mapping `Project` → `ProjectResponse`
    - Create `service/ProjectService.java`:
      - `createProject`: associate with authenticated user, persist, return `ProjectResponse`
      - `listProjects`: return only projects owned by authenticated user
      - `getProjectTasks`: verify project ownership (403 if mismatch, 404 if absent), return project's tasks
    - Create `controller/ProjectController.java` with `POST /api/projects`, `GET /api/projects`, `GET /api/projects/{id}/tasks`
    - _Requirements: 9.3, 9.4, 9.5_
  - [~] 10.3 Enforce project ownership on task creation with `projectId`
    - In `TaskService.createTask`: if `projectId` is present, load `Project`, verify owned by authenticated user (403 if mismatch, 404 if absent), set `task.project`
    - If `projectId` is absent, create task without project association
    - Add `@Valid` to `CreateProjectRequest` parameter in `ProjectController`
    - _Requirements: 9.6, 9.7, 9.8, 9.9_
  - [ ]* 10.4 Write property test P24 — POST /api/projects response contains all required fields
    - **Property 24: POST /api/projects response contains all required fields**
    - **Validates: Requirements 9.3**
    - Create `ProjectControllerPropertyTest`; generate valid `CreateProjectRequest`, call `POST /api/projects`, assert response contains `id`, `name`, `description`, `createdAt`, `updatedAt` with correct values
  - [ ]* 10.5 Write property test P25 — Project list contains only owner's projects
    - **Property 25: Project list contains only owner's projects**
    - **Validates: Requirements 9.4**
    - Create `ProjectOwnershipTest`; create projects for two distinct users, call `GET /api/projects` as U1, assert response contains only U1's projects
  - [ ]* 10.6 Write property test P26 — Task projectId association round-trip
    - **Property 26: Task projectId association round-trip**
    - **Validates: Requirements 9.6**
    - Create `TaskProjectAssociationTest`; generate user, project, and task payload with valid `projectId`, create task, retrieve via `GET /api/tasks/{id}`, assert project association matches
  - [ ]* 10.7 Write property test P27 — Cross-user project task assignment yields HTTP 403
    - **Property 27: Cross-user project task assignment yields HTTP 403**
    - **Validates: Requirements 9.8**
    - In `ProjectOwnershipTest`; generate user pair, project owned by U2, create task with `projectId = P.id` as U1, assert HTTP 403
  - [ ]* 10.8 Write property test P28 — Non-existent projectId yields HTTP 404 with all error fields
    - **Property 28: Non-existent projectId yields HTTP 404 with all error fields**
    - **Validates: Requirements 9.9**
    - Create `ProjectNotFoundTest`; generate `@Positive Long` IDs not seeded in DB, reference in task creation, assert HTTP 404 with JSON body containing `timestamp`, `status`, `error`, `message`
  - [ ]* 10.9 Write property test P29 — Blank project name rejected with field name
    - **Property 29: Blank project name is rejected with field name in response**
    - **Validates: Requirements 9.11**
    - Create `ProjectValidationTest`; generate whitespace-only strings, submit as `name` in `CreateProjectRequest`, assert HTTP 400 body contains `"name"`

- [~] 11. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery.
- Each task references specific requirements for full traceability.
- All 29 correctness properties from the design document are covered by individual property-based test sub-tasks.
- Property-based tests use jqwik (`net.jqwik:jqwik:1.9.x`) with a minimum of 100 iterations per test.
- Each jqwik test must include the comment: `// Feature: taskflow-architecture-upgrade, Property <N>: <property_text>`.
- H2 in PostgreSQL-compatible mode is used for `@DataJpaTest` and any property tests that touch the database.
- `@SpringBootTest` + `TestRestTemplate` is used for controller/integration property tests.
- `@WebMvcTest` + MockMvc is used for controller slice tests (no full context).
- Existing tests (`TaskControllerTest`, `TaskServiceTest`, `TaskRepositoryTest`) must be updated in tasks 2.1 and 3.3 to use the new DTO types and `TaskStatus` enum.

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3"] },
    { "id": 4, "tasks": ["3.4", "3.5", "4.1"] },
    { "id": 5, "tasks": ["4.2"] },
    { "id": 6, "tasks": ["4.3", "4.4", "4.5", "5.1"] },
    { "id": 7, "tasks": ["5.2", "5.3", "5.4", "6.1"] },
    { "id": 8, "tasks": ["6.2"] },
    { "id": 9, "tasks": ["6.3", "7.1"] },
    { "id": 10, "tasks": ["7.2"] },
    { "id": 11, "tasks": ["7.3"] },
    { "id": 12, "tasks": ["7.4", "7.5"] },
    { "id": 13, "tasks": ["7.6", "7.7", "7.8", "7.9", "7.10", "7.11", "7.12"] },
    { "id": 14, "tasks": ["9.1"] },
    { "id": 15, "tasks": ["9.2", "9.3"] },
    { "id": 16, "tasks": ["9.4", "9.5", "9.6", "9.7", "9.8"] },
    { "id": 17, "tasks": ["10.1"] },
    { "id": 18, "tasks": ["10.2", "10.3"] },
    { "id": 19, "tasks": ["10.4", "10.5", "10.6", "10.7", "10.8", "10.9"] }
  ]
}
```
