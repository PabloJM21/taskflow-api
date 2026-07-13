package com.taskapi.taskmanager.service;

import com.taskapi.taskmanager.dto.CreateTaskRequest;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.dto.UpdateTaskRequest;
import com.taskapi.taskmanager.entity.Project;
import com.taskapi.taskmanager.entity.Task;
import com.taskapi.taskmanager.entity.User;
import com.taskapi.taskmanager.exception.ProjectNotFoundException;
import com.taskapi.taskmanager.exception.TaskNotFoundException;
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
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository,
                       TaskMapper taskMapper,
                       UserRepository userRepository,
                       ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
    }

    // ── Get all tasks (filtered to the authenticated user's tasks) ──────────

    public List<TaskResponse> getAllTasks() {
        User currentUser = resolveCurrentUser();
        return taskRepository.findAllByOwner(currentUser)
                .stream()
                .map(taskMapper::toTaskResponse)
                .collect(Collectors.toList());
    }

    // ── Get task by ID — owner-only ─────────────────────────────────────────

    public TaskResponse getTaskById(Long id) {
        User currentUser = resolveCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        verifyOwnership(task, currentUser);
        return taskMapper.toTaskResponse(task);
    }

    // ── Create new task — associate with authenticated user ─────────────────

    public TaskResponse createTask(CreateTaskRequest request) {
        User currentUser = resolveCurrentUser();
        Task task = taskMapper.toTask(request);
        task.setOwner(currentUser);
        if (request.projectId() != null) {
            Project project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new ProjectNotFoundException(request.projectId()));
            if (!project.getOwner().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("You do not own this project");
            }
            task.setProject(project);
        }
        Task saved = taskRepository.save(task);
        return taskMapper.toTaskResponse(saved);
    }

    // ── Update task (partial) — owner-only ──────────────────────────────────

    public TaskResponse updateTask(Long id, UpdateTaskRequest request) {
        User currentUser = resolveCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        verifyOwnership(task, currentUser);

        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        Task saved = taskRepository.save(task);
        return taskMapper.toTaskResponse(saved);
    }

    // ── Delete task — owner-only ─────────────────────────────────────────────

    public void deleteTask(Long id) {
        User currentUser = resolveCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        verifyOwnership(task, currentUser);
        taskRepository.deleteById(id);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the currently authenticated user from the SecurityContext.
     * Throws {@link UsernameNotFoundException} (→ HTTP 401) if the principal does not
     * map to any user in the database, or if there is no authentication at all.
     */
    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UsernameNotFoundException("No authenticated user in security context");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }

    /**
     * Verifies that the given task is owned by the given user.
     * Throws {@link AccessDeniedException} (→ HTTP 403) on mismatch.
     */
    private void verifyOwnership(Task task, User currentUser) {
        if (!task.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to access this task");
        }
    }
}
