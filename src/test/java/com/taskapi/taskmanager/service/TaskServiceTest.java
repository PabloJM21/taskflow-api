package com.taskapi.taskmanager.service;

import com.taskapi.taskmanager.model.Task;
import com.taskapi.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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
    
    @InjectMocks
    private TaskService taskService;
    
    private Task task;
    
    @BeforeEach
    public void setUp() {
        task = new Task();
        task.setId(1L);
        task.setTitle("Test Task");
        task.setDescription("Test Description");
        task.setCompleted(false);
    }
    
    @Test
    public void testGetAllTasks() {
        Task task2 = new Task();
        task2.setId(2L);
        task2.setTitle("Task 2");
        task2.setCompleted(true);
        
        List<Task> tasks = Arrays.asList(task, task2);
        when(taskRepository.findAll()).thenReturn(tasks);
        
        List<Task> result = taskService.getAllTasks();
        
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(task, task2);
        verify(taskRepository, times(1)).findAll();
    }
    
    @Test
    public void testGetTaskById_Found() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        Optional<Task> result = taskService.getTaskById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test Task");
        verify(taskRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetTasksByStatus() {
        List<Task> completedTasks = List.of(task);
        when(taskRepository.findByCompleted(true)).thenReturn(completedTasks);

        List<Task> result = taskService.getTasksByStatus(true);

        assertThat(result).containsExactly(task);
        verify(taskRepository, times(1)).findByCompleted(true);
    }
    
    @Test
    public void testGetTaskById_NotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        
        Optional<Task> result = taskService.getTaskById(999L);

        assertThat(result).isEmpty();
        verify(taskRepository, times(1)).findById(999L);
    }
    
    @Test
    public void testCreateTask() {
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        Task result = taskService.createTask(task);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Task");
        verify(taskRepository, times(1)).save(task);
    }
    
    @Test
    public void testUpdateTask_Success() {
        Task updatedDetails = new Task();
        updatedDetails.setTitle("Updated Title");
        updatedDetails.setDescription("Updated Description");
        updatedDetails.setCompleted(true);
        
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        Task result = taskService.updateTask(1L, updatedDetails);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getDescription()).isEqualTo("Updated Description");
        assertThat(result.getCompleted()).isTrue();
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).save(task);
    }
    
    @Test
    public void testUpdateTask_NotFound() {
        Task updatedDetails = new Task();
        updatedDetails.setTitle("Updated Title");
        
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> taskService.updateTask(999L, updatedDetails))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Task not found with id: 999");
        
        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).save(any(Task.class));
    }
    
    @Test
    public void testDeleteTask() {
        doNothing().when(taskRepository).deleteById(1L);

        taskService.deleteTask(1L);
        
        verify(taskRepository, times(1)).deleteById(1L);
    }
}