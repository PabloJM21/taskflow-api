package com.taskapi.taskmanager.model;

import java.time.LocalDateTime;

/**
 * @deprecated Use {@link com.taskapi.taskmanager.entity.Task} instead.
 * This plain POJO is kept temporarily to avoid breaking any remaining references
 * while the migration to the entity package is completed.
 */
@Deprecated
public class Task {

    private Long id;
    private String title;
    private String description;
    private Boolean completed = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Task() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Boolean getCompleted() { return completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
