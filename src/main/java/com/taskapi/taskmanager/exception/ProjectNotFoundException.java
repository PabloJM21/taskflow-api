package com.taskapi.taskmanager.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long id) {
        super("Project not found with id: " + id);
    }

    public ProjectNotFoundException(String message) {
        super(message);
    }
}
