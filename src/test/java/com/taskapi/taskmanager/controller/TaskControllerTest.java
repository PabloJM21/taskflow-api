package com.taskapi.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskapi.taskmanager.model.Task;
import com.taskapi.taskmanager.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
public class TaskControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockitoBean
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
    public void testGetAllTasks() throws Exception {
        Task task2 = new Task();
        task2.setId(2L);
        task2.setTitle("Task 2");
        task2.setCompleted(true);
        
        List<Task> tasks = Arrays.asList(task, task2);
        when(taskService.getAllTasks()).thenReturn(tasks);
        
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Test Task")))
                .andExpect(jsonPath("$[1].title", is("Task 2")));
        
        verify(taskService, times(1)).getAllTasks();
    }
    
    @Test
    public void testGetTaskById_Found() throws Exception {
        when(taskService.getTaskById(1L)).thenReturn(Optional.of(task));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Task")))
                .andExpect(jsonPath("$.description", is("Test Description")))
                .andExpect(jsonPath("$.completed", is(false)));
        
        verify(taskService, times(1)).getTaskById(1L);
    }

    @Test
    public void testGetTasksByCompletedStatus() throws Exception {
        Task completedTask = new Task();
        completedTask.setId(2L);
        completedTask.setTitle("Completed Task");
        completedTask.setCompleted(true);

        when(taskService.getTasksByStatus(true)).thenReturn(List.of(completedTask));

        mockMvc.perform(get("/api/tasks").param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Completed Task")))
                .andExpect(jsonPath("$[0].completed", is(true)));

        verify(taskService, times(1)).getTasksByStatus(true);
    }
    
    @Test
    public void testGetTaskById_NotFound() throws Exception {
        when(taskService.getTaskById(999L)).thenReturn(Optional.empty());
        
        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound());
        
        verify(taskService, times(1)).getTaskById(999L);
    }
    
    @Test
    public void testCreateTask() throws Exception {
        when(taskService.createTask(any(Task.class))).thenReturn(task);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Task")));
        
        verify(taskService, times(1)).createTask(any(Task.class));
    }
    
    @Test
    public void testCreateTask_InvalidData() throws Exception {
        Task invalidTask = new Task();
        invalidTask.setTitle("");  // Invalid: blank title
        invalidTask.setDescription("Description");
        
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTask)))
                .andExpect(status().isBadRequest());
        
        verify(taskService, never()).createTask(any(Task.class));
    }
    
    @Test
    public void testUpdateTask() throws Exception {
        Task updatedTask = new Task();
        updatedTask.setId(1L);
        updatedTask.setTitle("Updated Title");
        updatedTask.setDescription("Updated Description");
        updatedTask.setCompleted(true);
        
        when(taskService.updateTask(eq(1L), any(Task.class))).thenReturn(updatedTask);

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.completed", is(true)));
        
        verify(taskService, times(1)).updateTask(eq(1L), any(Task.class));
    }
    
    @Test
    public void testUpdateTask_NotFound() throws Exception {
        when(taskService.updateTask(eq(999L), any(Task.class)))
                .thenThrow(new RuntimeException("Task not found with id: 999"));
        
        mockMvc.perform(put("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isNotFound());
        
        verify(taskService, times(1)).updateTask(eq(999L), any(Task.class));
    }
    
    @Test
    public void testDeleteTask() throws Exception {
        doNothing().when(taskService).deleteTask(1L);

        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
        
        verify(taskService, times(1)).deleteTask(1L);
    }
}