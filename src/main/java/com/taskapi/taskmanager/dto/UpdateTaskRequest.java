package com.taskapi.taskmanager.dto;

import com.taskapi.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTaskRequest(
    @Size(max = 255) String title,
    @Size(max = 1000) String description,
    TaskStatus status,
    String priority,
    LocalDate dueDate,
    Long projectId
) {}
