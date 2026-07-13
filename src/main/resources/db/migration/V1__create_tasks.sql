CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'TODO',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
