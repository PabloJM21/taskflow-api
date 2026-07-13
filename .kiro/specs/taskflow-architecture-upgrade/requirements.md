# Requirements Document

## Introduction

This document captures the requirements for the TaskFlow Architecture Upgrade — a comprehensive refactoring of a Spring Boot task manager application. The upgrade evolves the codebase from a flat layered architecture (controller/service/repository/model) to a feature-oriented package structure, introduces proper domain modeling with enums and DTOs, adds robust exception handling and validation, migrates to schema-versioned database migrations via Flyway, integrates JWT-based authentication with Spring Security, and extends the domain model with Projects as a grouping entity for tasks.

The upgrade is structured in nine incremental steps that can be delivered sequentially, each building on the previous.

---

## Glossary

- **Application**: The Spring Boot task manager being upgraded.
- **Task**: A unit of work with a title, description, status, priority, due date, and an owner (User).
- **TaskStatus**: An enumeration representing the lifecycle state of a Task: `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`, `CANCELLED`.
- **Project**: A named grouping entity that owns one or more Tasks.
- **User**: An authenticated application user who can own Tasks and belong to Roles.
- **Role**: An authorization designation assigned to a User (e.g., `ROLE_USER`, `ROLE_ADMIN`).
- **DTO (Data Transfer Object)**: A plain Java object used to carry data across API boundaries, separate from JPA entities.
- **TaskResponse**: The DTO returned by the API when representing a Task.
- **CreateTaskRequest**: The DTO accepted by the API when creating a Task.
- **UpdateTaskRequest**: The DTO accepted by the API when updating a Task.
- **Mapper**: A component (e.g., MapStruct) responsible for converting between entities and DTOs.
- **GlobalExceptionHandler**: The `@RestControllerAdvice` component that intercepts exceptions and returns structured error responses.
- **Flyway**: The database migration tool that manages versioned SQL scripts.
- **JWT (JSON Web Token)**: A signed token used for stateless authentication.
- **JwtFilter**: The Spring Security filter that extracts and validates a JWT from each incoming request.
- **Package**: A Java package namespace grouping related classes.
- **Feature Package**: A package organized around a business domain (e.g., `task/`, `user/`, `project/`) rather than a technical layer.

---

## Requirements

### Requirement 1: Feature-Oriented Package Structure

**User Story:** As a developer, I want the codebase organized by feature domain rather than technical layer, so that all classes related to a business concern are co-located and easier to navigate and maintain.

#### Acceptance Criteria

1. THE Application SHALL organize source files under the following base packages: `config/`, `controller/`, `dto/`, `entity/`, `exception/`, `mapper/`, `repository/`, `security/`, `service/`, `validation/`.
2. WHEN the codebase contains 3 or more distinct feature domains (e.g., task, user, project), THE Application SHALL support evolution to feature-level packages: `task/`, `user/`, `project/`, `comment/`, `notification/`, where each feature package contains its own controller, service, repository, entity, mapper, and DTO classes.
3. THE Application SHALL place each class in exactly one package whose name matches the class's primary stereotype (entity → `entity/`, controller → `controller/`, etc.).
4. IF placing a class in a feature package would create a mutual import cycle between two feature packages, THEN THE Application SHALL place that class in the shared base package whose name matches the class's stereotype (e.g., `entity/`, `service/`).
5. WHEN migrating from layer-based to feature-based packages, THE Application SHALL not alter public API contracts (endpoint paths, request/response bodies, HTTP status codes) during the migration.

---

### Requirement 2: TaskStatus Enum

**User Story:** As a developer, I want task completion modeled as a multi-state enum instead of a boolean, so that the full lifecycle of a task can be tracked accurately.

#### Acceptance Criteria

1. THE Application SHALL define a `TaskStatus` enum with values: `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`, `CANCELLED`.
2. THE Task entity SHALL replace the `boolean completed` field with a `TaskStatus status` field.
3. WHEN a new Task is created without an explicit status, THE Application SHALL assign `TaskStatus.TODO` as the default value.
4. WHEN a Task status update or creation is requested with a value not in the `TaskStatus` enum, THE Application SHALL return HTTP 400 with an error message that includes the invalid value and the set of accepted values.
5. THE Application SHALL persist `TaskStatus` values as their string name (e.g., `"IN_PROGRESS"`) in the database column.
6. THE Application SHALL serialize each `TaskStatus` value to a JSON string equal to its enum name, and deserialize that same JSON string back to the original `TaskStatus` value without data loss.

---

### Requirement 3: Data Transfer Objects (DTOs)

**User Story:** As a developer, I want API request and response bodies to use dedicated DTO classes rather than JPA entities, so that the API contract is decoupled from the persistence model.

#### Acceptance Criteria

1. THE Application SHALL define a `TaskResponse` DTO containing all nine fields: `id`, `title`, `description`, `status`, `priority`, `dueDate`, `createdAt`, `updatedAt`, and `ownerUsername`; a `TaskResponse` missing any of these fields is invalid.
2. THE Application SHALL define a `CreateTaskRequest` DTO where `title` is mandatory (non-blank, max 255 characters), `description` is optional (max 1000 characters), `status` is mandatory, `priority` is optional, and `dueDate` is optional.
3. THE Application SHALL define an `UpdateTaskRequest` DTO where all fields (`title`, `description`, `status`, `priority`, `dueDate`) are optional, and WHEN a field is absent from the request body, THE Application SHALL leave the corresponding entity field unchanged.
4. THE TaskController SHALL accept `CreateTaskRequest` as the request body for task creation endpoints.
5. THE TaskController SHALL accept `UpdateTaskRequest` as the request body for task update endpoints.
6. THE TaskController SHALL return `TaskResponse` (or a collection thereof) for all task read endpoints.
7. THE Mapper SHALL convert a `Task` entity to a `TaskResponse` DTO such that the values of all nine fields (`id`, `title`, `description`, `status`, `priority`, `dueDate`, `createdAt`, `updatedAt`, `ownerUsername`) in the resulting DTO equal the corresponding field values of the source entity.
8. THE Mapper SHALL convert a `CreateTaskRequest` DTO to a `Task` entity.
9. THE Application SHALL preserve the `id`, `title`, `description`, `status`, `priority`, and `dueDate` fields when mapping a `Task` entity to a `TaskResponse` DTO, such that the DTO field values equal the entity field values for each of those six fields.
10. WHEN a `CreateTaskRequest` with a blank or absent `title` is received, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field.

---

### Requirement 4: Global Exception Handling

**User Story:** As a developer, I want all unhandled exceptions to be caught centrally and returned as structured JSON error responses, so that clients always receive a consistent and informative error payload.

#### Acceptance Criteria

1. THE Application SHALL define a `GlobalExceptionHandler` annotated with `@RestControllerAdvice`.
2. THE Application SHALL define a `TaskNotFoundException` that extends `RuntimeException`.
3. WHEN a requested Task does not exist, THE Application SHALL throw `TaskNotFoundException` instead of returning `null`.
4. WHEN `TaskNotFoundException` is thrown, THE GlobalExceptionHandler SHALL return HTTP 404 with a JSON body containing all four required fields: `timestamp` (ISO 8601 format), `status`, `error`, and `message`; responses missing any of these fields are invalid.
5. WHEN a `MethodArgumentNotValidException` is thrown by bean validation, THE GlobalExceptionHandler SHALL return HTTP 400 with a JSON body containing a list of objects, each having a `field` property and a `message` property describing the validation failure for that field.
6. WHEN an unhandled `Exception` is thrown, THE GlobalExceptionHandler SHALL return HTTP 500 with a response body that contains no internal class names, no stack trace details, and no implementation-specific identifiers.
7. THE GlobalExceptionHandler SHALL never expose internal stack traces in the HTTP response body.

---

### Requirement 5: Input Validation

**User Story:** As a developer, I want incoming request payloads to be validated automatically using Bean Validation annotations, so that invalid data is rejected before reaching the service layer.

#### Acceptance Criteria

1. WHEN a `CreateTaskRequest` with a blank `title` is received, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`title`).
2. WHEN a `CreateTaskRequest` with a `title` exceeding 255 characters is received, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`title`).
3. WHEN a `CreateTaskRequest` with a null `status` is received, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`status`).
4. WHEN a `CreateTaskRequest` with a `title` of 255 characters or fewer and a non-null `status` is received, THE Application SHALL not return HTTP 400 for either of those two fields.
5. WHEN a User registration request contains a string that does not conform to the format `local-part@domain` (where `local-part` is at least 1 character and `domain` contains at least one dot) is received for the `email` field, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`email`).
6. WHEN a User registration request contains a blank `email` field, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`email`).

---

### Requirement 6: Database Schema Migrations with Flyway

**User Story:** As a developer, I want the database schema managed by versioned SQL migration scripts, so that schema changes are tracked, repeatable, and safe to apply in any environment.

#### Acceptance Criteria

1. THE Application SHALL replace `spring.jpa.hibernate.ddl-auto=update` with Flyway as the sole mechanism for managing the database schema.
2. THE Application SHALL include a migration `V1__create_tasks.sql` that creates the initial `tasks` table with columns: `id`, `title`, `description`, `status`, `created_at`, `updated_at`.
3. THE Application SHALL include a migration `V2__add_priority.sql` that adds a `priority` column to the `tasks` table.
4. THE Application SHALL include a migration `V3__add_due_date.sql` that adds a `due_date` column to the `tasks` table.
5. WHEN the Application starts with pending migrations, THE Flyway SHALL apply all pending migrations in ascending version order before the application begins serving requests.
6. WHEN all migrations have already been applied and the Application starts again, THE Flyway SHALL apply no migrations and the Application SHALL begin serving requests normally.
7. WHEN a previously applied migration script is modified, THE Flyway SHALL detect the checksum mismatch and prevent the Application from starting, logging a descriptive error identifying the affected migration file.
8. IF a migration script fails during execution, THEN THE Application SHALL fail to start, log the migration error, and leave the database in the state it was in before that migration script began executing.

---

### Requirement 7: JWT-Based Authentication with Spring Security

**User Story:** As a developer, I want the application secured with JWT-based stateless authentication, so that only authenticated users can access protected resources.

#### Acceptance Criteria

1. THE Application SHALL integrate Spring Security with a stateless session policy (`SessionCreationPolicy.STATELESS`).
2. THE Application SHALL define a `User` entity with fields: `id`, `username`, `password` (BCrypt-hashed), `email`, and a collection of `Role` entities.
3. THE Application SHALL define a `Role` entity with a `name` field (e.g., `ROLE_USER`, `ROLE_ADMIN`).
4. WHEN a valid `POST /api/auth/register` request is received, THE Application SHALL create a new User with the `ROLE_USER` role and return a JWT in the response body.
5. WHEN a `POST /api/auth/login` request is received with valid credentials, THE Application SHALL return a JWT with an expiry of 3600 seconds in the response body.
6. WHEN a request arrives without a JWT, or with a JWT whose signature does not verify, or with a JWT whose structure is malformed, THE JwtFilter SHALL reject the request with HTTP 401.
7. WHEN a request arrives with a JWT whose expiry time is in the past, THE JwtFilter SHALL reject the request with HTTP 401 and a response body indicating token expiry.
8. WHEN a request arrives with a valid JWT, THE JwtFilter SHALL populate the `SecurityContext` with the authenticated user's username and granted authorities derived from that JWT.
9. WHEN an unauthenticated request is made to any `TaskController` endpoint, THE Application SHALL return HTTP 401.
10. WHEN a `POST /api/auth/login` is made with incorrect credentials, THE Application SHALL return HTTP 401 with a message indicating authentication failure.
11. WHEN a User with only `ROLE_USER` attempts to access an administrative endpoint, THE Application SHALL return HTTP 403.
12. WHEN a `POST /api/auth/register` request is received with a username or email that already exists, THE Application SHALL return HTTP 409.

---

### Requirement 8: Task Ownership

**User Story:** As a user, I want tasks to belong to me specifically, so that I only see and manage my own tasks.

#### Acceptance Criteria

1. THE Task entity SHALL include a `ManyToOne` relationship to the `User` entity representing the task owner.
2. WHEN a Task is created, THE Application SHALL associate the Task with the authenticated User derived from the JWT.
3. WHEN a User requests the list of tasks, THE Application SHALL return only Tasks whose owner matches the authenticated User.
4. WHEN a User attempts to read, update, or delete a Task not owned by that User, THE Application SHALL return HTTP 403 with a response body indicating access is denied.
5. THE Application SHALL include a Flyway migration `V4__add_user_id_to_tasks.sql` that adds the `user_id` foreign key column to the `tasks` table.
6. WHEN a request arrives with a valid JWT whose subject does not correspond to any existing User, THE Application SHALL return HTTP 401.

---

### Requirement 9: Project Entity

**User Story:** As a user, I want to group tasks under named projects, so that I can organize my work by context such as Frontend, Backend, or Personal.

#### Acceptance Criteria

1. THE Application SHALL define a `Project` entity with fields: `id`, `name` (non-blank, max 100 characters), `description` (optional, max 500 characters), `createdAt`, `updatedAt`, and an owner `User`.
2. THE Task entity SHALL include an optional `ManyToOne` relationship to the `Project` entity.
3. WHEN a valid `POST /api/projects` request is received, THE Application SHALL create a new Project owned by the authenticated User and return a response body containing the created Project's `id`, `name`, `description`, `createdAt`, and `updatedAt`.
4. WHEN a `GET /api/projects` request is received, THE Application SHALL return only Projects owned by the authenticated User.
5. WHEN a `GET /api/projects/{projectId}/tasks` request is received and the Project is owned by the authenticated User, THE Application SHALL return all Tasks belonging to that Project; WHEN the Project exists but is not owned by the authenticated User, THE Application SHALL return HTTP 403; WHEN the Project does not exist, THE Application SHALL return HTTP 404.
6. WHEN a Task is created with a `projectId` field, THE Application SHALL associate the Task with the specified Project.
7. WHEN a Task is created without a `projectId` field, THE Application SHALL create the Task without a Project association.
8. WHEN a User attempts to add a Task to a Project not owned by that User but the Project exists, THE Application SHALL return HTTP 403.
9. WHEN a Project that does not exist is referenced in a task creation or assignment request, THE Application SHALL return HTTP 404 with a JSON body containing the fields `timestamp`, `status`, `error`, and `message`.
10. THE Application SHALL include Flyway migrations to create the `projects` table and add the `project_id` foreign key column to the `tasks` table.
11. WHEN a `POST /api/projects` request is received with a blank or absent `name`, THE Application SHALL return HTTP 400 with a response body that contains the name of the failing field (`name`).
