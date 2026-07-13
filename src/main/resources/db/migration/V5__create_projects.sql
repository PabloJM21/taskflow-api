-- Create projects table
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    owner_id BIGINT NOT NULL REFERENCES users(id)
);

-- Add project_id FK column to tasks
ALTER TABLE tasks ADD COLUMN project_id BIGINT REFERENCES projects(id);
