package com.taskapi.taskmanager.mapper;

import com.taskapi.taskmanager.dto.CreateTaskRequest;
import com.taskapi.taskmanager.dto.TaskResponse;
import com.taskapi.taskmanager.entity.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(source = "owner.username", target = "ownerUsername")
    TaskResponse toTaskResponse(Task task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toTask(CreateTaskRequest request);
}
