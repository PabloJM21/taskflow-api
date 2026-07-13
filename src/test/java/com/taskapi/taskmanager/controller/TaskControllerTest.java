package com.taskapi.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskapi.taskmanager.dto.CreateTaskRequest;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.dto.UpdateTaskRequest;
import com.taskapi.taskmanager.entity.TaskStatus;
import com.taskapi.taskmanager.exception.TaskNotFoundException;
import com.taskapi.taskmanager.security.JwtUtil;
import com.taskapi.taskmanager.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    // JwtFilter is a @Component picked up by the WebMvcTest slice; mock its dependencies
    // so the security filter chain can be instantiated (addFilters=false disables execution).
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private TaskResponse taskResponse;

    @BeforeEach
    public void setUp() {
        taskResponse = new TaskResponse(
                1L,
                "Test Task",
                "Test Description",
                TaskStatus.TODO,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "testuser"
        );
    }

    @Test
    public void testGetAllTasks() throws Exception {
        TaskResponse taskResponse2 = new TaskResponse(
                2L, "Task 2", null, TaskStatus.DONE, null, null,
                LocalDateTime.now(), LocalDateTime.now(), "testuser"
        );

        List<TaskResponse> tasks = Arrays.asList(taskResponse, taskResponse2);
        when(taskService.getAllTasks()).thenReturn(tasks);

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Test Task")))
                .andExpect(jsonPath("$[0].ownerUsername", is("testuser")))
                .andExpect(jsonPath("$[1].title", is("Task 2")))
                .andExpect(jsonPath("$[1].ownerUsername", is("testuser")));

        verify(taskService, times(1)).getAllTasks();
    }

    @Test
    public void testGetTaskById_Found() throws Exception {
        when(taskService.getTaskById(1L)).thenReturn(taskResponse);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Task")))
                .andExpect(jsonPath("$.description", is("Test Description")))
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.ownerUsername", is("testuser")));

        verify(taskService, times(1)).getTaskById(1L);
    }

    @Test
    public void testGetTaskById_NotFound() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("999")))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(taskService, times(1)).getTaskById(999L);
    }

    @Test
    public void testCreateTask() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                "Test Task", "Test Description", TaskStatus.TODO, null, null, null
        );
        when(taskService.createTask(any(CreateTaskRequest.class))).thenReturn(taskResponse);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Task")))
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.ownerUsername", is("testuser")));

        verify(taskService, times(1)).createTask(any(CreateTaskRequest.class));
    }

    @Test
    public void testCreateTask_InvalidData() throws Exception {
        // Blank title should fail @NotBlank validation
        CreateTaskRequest invalidRequest = new CreateTaskRequest(
                "", "Description", TaskStatus.TODO, null, null, null
        );

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).createTask(any(CreateTaskRequest.class));
    }

    @Test
    public void testUpdateTask() throws Exception {
        TaskResponse updatedResponse = new TaskResponse(
                1L, "Updated Title", "Updated Description", TaskStatus.DONE, null, null,
                LocalDateTime.now(), LocalDateTime.now(), "testuser"
        );
        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "Updated Title", "Updated Description", TaskStatus.DONE, null, null, null
        );

        when(taskService.updateTask(eq(1L), any(UpdateTaskRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.ownerUsername", is("testuser")));

        verify(taskService, times(1)).updateTask(eq(1L), any(UpdateTaskRequest.class));
    }

    @Test
    public void testUpdateTask_NotFound() throws Exception {
        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "Updated Title", null, null, null, null, null
        );
        when(taskService.updateTask(eq(999L), any(UpdateTaskRequest.class)))
                .thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(put("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(taskService, times(1)).updateTask(eq(999L), any(UpdateTaskRequest.class));
    }

    @Test
    public void testDeleteTask() throws Exception {
        doNothing().when(taskService).deleteTask(1L);

        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());

        verify(taskService, times(1)).deleteTask(1L);
    }
}
