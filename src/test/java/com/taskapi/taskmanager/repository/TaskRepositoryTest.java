package com.taskapi.taskmanager.repository;

import com.taskapi.taskmanager.model.Task;
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

    @Test
    public void testSaveTask() {
        Task task = new Task();
        task.setTitle("Test Title");
        task.setDescription("Test Description");
        task.setCompleted(false);

        Task savedTask = taskRepository.save(task);

        assertThat(savedTask).isNotNull();
        assertThat(savedTask.getId()).isGreaterThan(0);
        assertThat(savedTask.getTitle()).isEqualTo("Test Title");
    }

    @Test
    public void testFindAllTasks() {
        Task task1 = new Task();
        task1.setTitle("Task 1");
        task1.setCompleted(false);

        Task task2 = new Task();
        task2.setTitle("Task 2");
        task2.setCompleted(true);

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
        task.setCompleted(false);
        Task savedTask = taskRepository.save(task);

        Optional<Task> foundTask = taskRepository.findById(savedTask.getId());

        assertThat(foundTask).isPresent();
        assertThat(foundTask.get().getTitle()).isEqualTo("Find me!");
    }

    @Test
    public void testUpdateTask() {
        Task task = new Task();
        task.setTitle("OG Title");
        task.setCompleted(false);
        Task savedTask = taskRepository.save(task);

        savedTask.setTitle("Updated Title");
        savedTask.setCompleted(true);
        Task updatedTask = taskRepository.save(savedTask);

        assertThat(updatedTask.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedTask.getCompleted()).isTrue();
    }

    @Test
    public void testFindTasksByCompletedStatus() {
        Task openTask = new Task();
        openTask.setTitle("Open Task");
        openTask.setCompleted(false);

        Task completedTask = new Task();
        completedTask.setTitle("Completed Task");
        completedTask.setCompleted(true);

        taskRepository.save(openTask);
        taskRepository.save(completedTask);

        List<Task> completedTasks = taskRepository.findByCompleted(true);

        assertThat(completedTasks).hasSize(1);
        assertThat(completedTasks.get(0).getTitle()).isEqualTo("Completed Task");
        assertThat(completedTasks.get(0).getCompleted()).isTrue();
    }

    @Test
    public void testDeleteTask() {
        Task task = new Task();
        task.setTitle("Delete");
        task.setCompleted(false);
        Task savedTask = taskRepository.save(task);

        taskRepository.deleteById(savedTask.getId());
        Optional<Task> deletedTask = taskRepository.findById(savedTask.getId());

        assertThat(deletedTask).isEmpty();
    }
}
