package com.taskapi.taskmanager.repository;

import com.taskapi.taskmanager.entity.Project;
import com.taskapi.taskmanager.entity.Task;
import com.taskapi.taskmanager.entity.TaskStatus;
import com.taskapi.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByTitleContainingIgnoreCase(String keyword);

    List<Task> findAllByOwner(User owner);

    List<Task> findAllByProject(Project project);
}
