package com.taskapi.taskmanager.mapper;

import com.taskapi.taskmanager.dto.ProjectResponse;
import com.taskapi.taskmanager.entity.Project;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toProjectResponse(Project project);
}
