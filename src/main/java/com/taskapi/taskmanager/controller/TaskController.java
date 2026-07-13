package com.taskapi.taskmanager.controller;

import com.taskapi.taskmanager.dto.CreateTaskRequest;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.dto.UpdateTaskRequest;
import com.taskapi.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    // GET /api/tasks — list all tasks
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    // GET /api/tasks/{id} — get task by ID (throws TaskNotFoundException → 404 via GlobalExceptionHandler)
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    // POST /api/tasks — create new task
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/tasks/{id} — update task (throws TaskNotFoundException → 404 via GlobalExceptionHandler)
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse updated = taskService.updateTask(id, request);
        return ResponseEntity.ok(updated);
    }

    // DELETE /api/tasks/{id} — delete task
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
