package com.coolplanet.tasktracker.controller;

import com.coolplanet.tasktracker.dto.TaskAverageResponse;
import com.coolplanet.tasktracker.dto.TaskDurationRequest;
import com.coolplanet.tasktracker.exception.GlobalExceptionHandler;
import com.coolplanet.tasktracker.exception.TaskNotFoundException;
import com.coolplanet.tasktracker.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    private static final String BASE = "/api/v1/tasks";

    @Test
    void recordDuration_returns200_forValidPositiveDuration() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(1500L)))
                .andExpect(status().isOk());

        verify(taskService).recordDuration("task-1", 1500L);
    }

    @Test
    void recordDuration_returns200_forZeroDuration() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(0L)))
                .andExpect(status().isOk());

        verify(taskService).recordDuration("task-1", 0L);
    }

    @ParameterizedTest(name = "taskId={0}")
    @ValueSource(strings = {"simple", "with-dash", "WITH_UNDERSCORE", "task.name"})
    void recordDuration_accepts_variousTaskIdFormats(String taskId) throws Exception {
        mockMvc.perform(post(BASE + "/" + taskId + "/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(100L)))
                .andExpect(status().isOk());
    }

    @Test
    void recordDuration_returns400_whenDurationIsNegative() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(taskService);
    }

    @Test
    void recordDuration_returns400_whenDurationIsNull() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void recordDuration_returns400_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void recordDuration_returns400_whenContentTypeIsAbsent() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .content(json(100L)))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(taskService);
    }

    @Test
    void recordDuration_errorBody_containsStatusField() throws Exception {
        mockMvc.perform(post(BASE + "/task-1/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\": -99}"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getAverage_returns200_withCorrectTaskId() throws Exception {
        when(taskService.getAverage("task-1"))
                .thenReturn(new TaskAverageResponse("task-1", 250.0));

        mockMvc.perform(get(BASE + "/task-1/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-1"));
    }

    @Test
    void getAverage_returns200_withCorrectAverageDuration() throws Exception {
        when(taskService.getAverage("task-1"))
                .thenReturn(new TaskAverageResponse("task-1", 250.0));

        mockMvc.perform(get(BASE + "/task-1/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDurationMs").value(250.0));
    }

    @Test
    void getAverage_propagatesZeroMean_correctly() throws Exception {
        when(taskService.getAverage("task-1"))
                .thenReturn(new TaskAverageResponse("task-1", 0.0));

        mockMvc.perform(get(BASE + "/task-1/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDurationMs").value(0.0));
    }

    @Test
    void getAverage_responseHasNoExtraFields() throws Exception {
        when(taskService.getAverage("task-1"))
                .thenReturn(new TaskAverageResponse("task-1", 100.0));

        mockMvc.perform(get(BASE + "/task-1/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAverage_returns404_whenTaskIsUnknown() throws Exception {
        when(taskService.getAverage("ghost"))
                .thenThrow(new TaskNotFoundException("ghost"));

        mockMvc.perform(get(BASE + "/ghost/average"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAverage_404Body_containsStatus() throws Exception {
        when(taskService.getAverage("ghost"))
                .thenThrow(new TaskNotFoundException("ghost"));

        mockMvc.perform(get(BASE + "/ghost/average"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getAverage_404Message_mentionsTaskId() throws Exception {
        when(taskService.getAverage("ghost"))
                .thenThrow(new TaskNotFoundException("ghost"));

        mockMvc.perform(get(BASE + "/ghost/average"))
                .andExpect(jsonPath("$.message", containsString("ghost")));
    }

    @Test
    void getAverage_404Body_containsTimestamp() throws Exception {
        when(taskService.getAverage("ghost"))
                .thenThrow(new TaskNotFoundException("ghost"));

        mockMvc.perform(get(BASE + "/ghost/average"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private String json(long durationMs) throws Exception {
        return objectMapper.writeValueAsString(new TaskDurationRequest(durationMs));
    }
}
