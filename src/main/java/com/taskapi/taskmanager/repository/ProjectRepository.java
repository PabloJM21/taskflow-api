package com.taskapi.taskmanager.repository;

import com.taskapi.taskmanager.entity.Project;
import com.taskapi.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOwner(User owner);
}
