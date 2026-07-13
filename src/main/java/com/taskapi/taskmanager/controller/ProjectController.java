package com.taskapi.taskmanager.controller;

import com.taskapi.taskmanager.dto.CreateProjectRequest;
import com.taskapi.taskmanager.dto.ProjectResponse;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // POST /api/projects — create a new project
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse created = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // GET /api/projects — list authenticated user's projects
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects() {
        return ResponseEntity.ok(projectService.listProjects());
    }

    // GET /api/projects/{id}/tasks — list tasks in project (owner only)
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<TaskResponse>> getProjectTasks(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTasks(id));
    }
}
