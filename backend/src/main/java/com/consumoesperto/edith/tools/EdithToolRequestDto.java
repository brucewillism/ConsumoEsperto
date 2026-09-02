package com.consumoesperto.edith.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Envelope oficial do callback {@code POST /api/internal/edith/tools}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EdithToolRequestDto {

    @JsonProperty("request_id")
    private String requestId;

    private String tool;

    private String version;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("project_id")
    private String projectId;

    private Map<String, Object> arguments;
}
