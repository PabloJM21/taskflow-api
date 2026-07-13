package com.taskapi.taskmanager.dto;

import com.taskapi.taskmanager.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    String priority,
    LocalDate dueDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String ownerUsername
) {}
