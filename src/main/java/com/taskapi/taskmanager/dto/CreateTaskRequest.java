package com.taskapi.taskmanager.dto;

import com.taskapi.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 1000) String description,
    @NotNull TaskStatus status,
    String priority,
    LocalDate dueDate,
    Long projectId
) {}
