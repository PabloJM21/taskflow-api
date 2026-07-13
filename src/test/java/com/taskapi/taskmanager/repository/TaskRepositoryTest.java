package com.taskapi.taskmanager.repository;

import com.taskapi.taskmanager.entity.Task;
import com.taskapi.taskmanager.entity.TaskStatus;
import com.taskapi.taskmanager.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    public void setUpUser() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("hashed-password");
        testUser.setEmail("testuser@example.com");
        testUser = userRepository.save(testUser);
    }

    @Test
    public void testSaveTask() {
        Task task = new Task();
        task.setTitle("Test Title");
        task.setDescription("Test Description");
        task.setStatus(TaskStatus.TODO);
        task.setOwner(testUser);

        Task savedTask = taskRepository.save(task);

        assertThat(savedTask).isNotNull();
        assertThat(savedTask.getId()).isGreaterThan(0);
        assertThat(savedTask.getTitle()).isEqualTo("Test Title");
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    public void testFindAllTasks() {
        Task task1 = new Task();
        task1.setTitle("Task 1");
        task1.setStatus(TaskStatus.TODO);
        task1.setOwner(testUser);

        Task task2 = new Task();
        task2.setTitle("Task 2");
        task2.setStatus(TaskStatus.DONE);
        task2.setOwner(testUser);

        taskRepository.save(task1);
        taskRepository.save(task2);

        List<Task> tasks = taskRepository.findAll();

        assertThat(tasks).isNotNull();
        assertThat(tasks.size()).isEqualTo(2);
    }

    @Test
    public void testFindTaskById() {
        Task task = new Task();
        task.setTitle("Find me!");
        task.setStatus(TaskStatus.TODO);
        task.setOwner(testUser);
        Task savedTask = taskRepository.save(task);

        Optional<Task> foundTask = taskRepository.findById(savedTask.getId());

        assertThat(foundTask).isPresent();
        assertThat(foundTask.get().getTitle()).isEqualTo("Find me!");
    }

    @Test
    public void testUpdateTask() {
        Task task = new Task();
        task.setTitle("OG Title");
        task.setStatus(TaskStatus.TODO);
        task.setOwner(testUser);
        Task savedTask = taskRepository.save(task);

        savedTask.setTitle("Updated Title");
        savedTask.setStatus(TaskStatus.DONE);
        Task updatedTask = taskRepository.save(savedTask);

        assertThat(updatedTask.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    public void testFindTasksByStatus() {
        Task openTask = new Task();
        openTask.setTitle("Open Task");
        openTask.setStatus(TaskStatus.TODO);
        openTask.setOwner(testUser);

        Task doneTask = new Task();
        doneTask.setTitle("Done Task");
        doneTask.setStatus(TaskStatus.DONE);
        doneTask.setOwner(testUser);

        taskRepository.save(openTask);
        taskRepository.save(doneTask);

        List<Task> doneTasks = taskRepository.findByStatus(TaskStatus.DONE);

        assertThat(doneTasks).hasSize(1);
        assertThat(doneTasks.get(0).getTitle()).isEqualTo("Done Task");
        assertThat(doneTasks.get(0).getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    public void testDeleteTask() {
        Task task = new Task();
        task.setTitle("Delete me");
        task.setStatus(TaskStatus.TODO);
        task.setOwner(testUser);
        Task savedTask = taskRepository.save(task);

        taskRepository.deleteById(savedTask.getId());
        Optional<Task> deletedTask = taskRepository.findById(savedTask.getId());

        assertThat(deletedTask).isEmpty();
    }
}
