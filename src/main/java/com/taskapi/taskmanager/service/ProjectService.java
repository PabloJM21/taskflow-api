package com.taskapi.taskmanager.service;

import com.taskapi.taskmanager.dto.CreateProjectRequest;
import com.taskapi.taskmanager.dto.ProjectResponse;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.entity.Project;
import com.taskapi.taskmanager.entity.User;
import com.taskapi.taskmanager.exception.ProjectNotFoundException;
import com.taskapi.taskmanager.mapper.ProjectMapper;
import com.taskapi.taskmanager.mapper.TaskMapper;
import com.taskapi.taskmanager.repository.ProjectRepository;
import com.taskapi.taskmanager.repository.TaskRepository;
import com.taskapi.taskmanager.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMapper projectMapper,
                          TaskRepository taskRepository,
                          TaskMapper taskMapper,
                          UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.userRepository = userRepository;
    }

    // ── Create project ────────────────────────────────────────────────────────

    public ProjectResponse createProject(CreateProjectRequest request) {
        User currentUser = resolveCurrentUser();
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwner(currentUser);
        Project saved = projectRepository.save(project);
        return projectMapper.toProjectResponse(saved);
    }

    // ── List projects (owner-only) ────────────────────────────────────────────

    public List<ProjectResponse> listProjects() {
        User currentUser = resolveCurrentUser();
        return projectRepository.findAllByOwner(currentUser)
                .stream()
                .map(projectMapper::toProjectResponse)
                .collect(Collectors.toList());
    }

    // ── Get tasks for a project (owner-only) ─────────────────────────────────

    public List<TaskResponse> getProjectTasks(Long projectId) {
        User currentUser = resolveCurrentUser();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        if (!project.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not own this project");
        }
        return taskRepository.findAllByProject(project)
                .stream()
                .map(taskMapper::toTaskResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the currently authenticated user from the SecurityContext.
     * Throws {@link UsernameNotFoundException} (→ HTTP 401) if no authenticated user exists.
     */
    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UsernameNotFoundException("No authenticated user in security context");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
