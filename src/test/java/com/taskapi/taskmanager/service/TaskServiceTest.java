package com.taskapi.taskmanager.service;

import com.taskapi.taskmanager.dto.CreateTaskRequest;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.dto.UpdateTaskRequest;
import com.taskapi.taskmanager.entity.Task;
import com.taskapi.taskmanager.entity.TaskStatus;
import com.taskapi.taskmanager.entity.User;
import com.taskapi.taskmanager.exception.TaskNotFoundException;
import com.taskapi.taskmanager.mapper.TaskMapper;
import com.taskapi.taskmanager.repository.TaskRepository;
import com.taskapi.taskmanager.repository.UserRepository;
import com.taskapi.taskmanager.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private TaskService taskService;

    private User currentUser;
    private Task task;
    private TaskResponse taskResponse;

    @BeforeEach
    public void setUp() {
        // Set up authenticated user in SecurityContext
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("testuser");
        currentUser.setPassword("hashed-password");
        currentUser.setEmail("testuser@example.com");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("testuser", null, Collections.emptyList());
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));

        // Default task owned by currentUser
        task = new Task();
        task.setId(1L);
        task.setTitle("Test Task");
        task.setDescription("Test Description");
        task.setStatus(TaskStatus.TODO);
        task.setOwner(currentUser);

        taskResponse = new TaskResponse(
                1L, "Test Task", "Test Description",
                TaskStatus.TODO, null, null,
                LocalDateTime.now(), LocalDateTime.now(), "testuser"
        );

        // Default stub: currentUser resolves from SecurityContext
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetAllTasks() {
        Task task2 = new Task();
        task2.setId(2L);
        task2.setTitle("Task 2");
        task2.setStatus(TaskStatus.DONE);
        task2.setOwner(currentUser);

        TaskResponse taskResponse2 = new TaskResponse(
                2L, "Task 2", null, TaskStatus.DONE, null, null,
                LocalDateTime.now(), LocalDateTime.now(), "testuser"
        );

        List<Task> tasks = Arrays.asList(task, task2);
        when(taskRepository.findAllByOwner(currentUser)).thenReturn(tasks);
        when(taskMapper.toTaskResponse(task)).thenReturn(taskResponse);
        when(taskMapper.toTaskResponse(task2)).thenReturn(taskResponse2);

        List<TaskResponse> result = taskService.getAllTasks();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Test Task");
        assertThat(result.get(1).title()).isEqualTo("Task 2");
        verify(taskRepository, times(1)).findAllByOwner(currentUser);
    }

    @Test
    public void testGetTaskById_Found() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toTaskResponse(task)).thenReturn(taskResponse);

        TaskResponse result = taskService.getTaskById(1L);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Test Task");
        assertThat(result.ownerUsername()).isEqualTo("testuser");
        verify(taskRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetTaskById_NotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
    }

    @Test
    public void testGetTaskById_ForbiddenForOtherUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setUsername("otheruser");

        Task otherTask = new Task();
        otherTask.setId(2L);
        otherTask.setTitle("Other Task");
        otherTask.setStatus(TaskStatus.TODO);
        otherTask.setOwner(otherUser);

        when(taskRepository.findById(2L)).thenReturn(Optional.of(otherTask));

        assertThatThrownBy(() -> taskService.getTaskById(2L))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, times(1)).findById(2L);
    }

    @Test
    public void testCreateTask() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Test Task", "Test Description", TaskStatus.TODO, null, null, null
        );
        // toTask returns a task without owner; the service sets the owner
        Task taskWithoutOwner = new Task();
        taskWithoutOwner.setTitle("Test Task");
        taskWithoutOwner.setStatus(TaskStatus.TODO);

        when(taskMapper.toTask(request)).thenReturn(taskWithoutOwner);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskMapper.toTaskResponse(task)).thenReturn(taskResponse);

        TaskResponse result = taskService.createTask(request);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Test Task");
        assertThat(result.status()).isEqualTo(TaskStatus.TODO);
        // Verify the owner was set before saving
        assertThat(taskWithoutOwner.getOwner()).isEqualTo(currentUser);
        verify(taskRepository, times(1)).save(taskWithoutOwner);
    }

    @Test
    public void testUpdateTask_Success() {
        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "Updated Title", "Updated Description", TaskStatus.IN_PROGRESS, null, null, null
        );

        Task updatedTask = new Task();
        updatedTask.setId(1L);
        updatedTask.setTitle("Updated Title");
        updatedTask.setDescription("Updated Description");
        updatedTask.setStatus(TaskStatus.IN_PROGRESS);
        updatedTask.setOwner(currentUser);

        TaskResponse updatedResponse = new TaskResponse(
                1L, "Updated Title", "Updated Description",
                TaskStatus.IN_PROGRESS, null, null,
                LocalDateTime.now(), LocalDateTime.now(), "testuser"
        );

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(updatedTask);
        when(taskMapper.toTaskResponse(updatedTask)).thenReturn(updatedResponse);

        TaskResponse result = taskService.updateTask(1L, updateRequest);

        assertThat(result.title()).isEqualTo("Updated Title");
        assertThat(result.description()).isEqualTo("Updated Description");
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    public void testUpdateTask_PartialUpdate_UnspecifiedFieldsUnchanged() {
        // Only title is provided; description and status should remain unchanged
        UpdateTaskRequest partialRequest = new UpdateTaskRequest(
                "New Title", null, null, null, null, null
        );

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskMapper.toTaskResponse(task)).thenReturn(taskResponse);

        taskService.updateTask(1L, partialRequest);

        // Title was updated, description and status remain as original
        assertThat(task.getTitle()).isEqualTo("New Title");
        assertThat(task.getDescription()).isEqualTo("Test Description");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    public void testUpdateTask_ForbiddenForOtherUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setUsername("otheruser");

        Task otherTask = new Task();
        otherTask.setId(2L);
        otherTask.setTitle("Other Task");
        otherTask.setStatus(TaskStatus.TODO);
        otherTask.setOwner(otherUser);

        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "Hacked Title", null, null, null, null, null
        );

        when(taskRepository.findById(2L)).thenReturn(Optional.of(otherTask));

        assertThatThrownBy(() -> taskService.updateTask(2L, updateRequest))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    public void testUpdateTask_NotFound() {
        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "Updated Title", null, null, null, null, null
        );
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateTask(999L, updateRequest))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    public void testDeleteTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(taskRepository).deleteById(1L);

        taskService.deleteTask(1L);

        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).deleteById(1L);
    }

    @Test
    public void testDeleteTask_ForbiddenForOtherUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setUsername("otheruser");

        Task otherTask = new Task();
        otherTask.setId(2L);
        otherTask.setTitle("Other Task");
        otherTask.setStatus(TaskStatus.TODO);
        otherTask.setOwner(otherUser);

        when(taskRepository.findById(2L)).thenReturn(Optional.of(otherTask));

        assertThatThrownBy(() -> taskService.deleteTask(2L))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).deleteById(any());
    }
}
