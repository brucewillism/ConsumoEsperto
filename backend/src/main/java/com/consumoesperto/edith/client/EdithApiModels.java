package com.consumoesperto.edith.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTOs alinhados ao contrato E.D.I.T.H. SDK 0.4.1 / API {@code /api/v1/integrations/*}.
 */
public final class EdithApiModels {

    private EdithApiModels() {
    }

    public static final class Paths {
        public static final String CONVERSATIONS = "/api/v1/integrations/conversations";
        public static final String HEALTH = "/api/v1/integrations/health";

        private Paths() {
        }

        public static String conversationMessages(String conversationId) {
            return CONVERSATIONS + "/" + conversationId + "/messages";
        }

        public static String task(String taskId) {
            return "/api/v1/integrations/tasks/" + taskId;
        }

        public static String taskEvents(String taskId) {
            return task(taskId) + "/events";
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreateConversationRequest {
        private String title;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConversationResponse {
        @JsonProperty("conversation_id")
        @JsonAlias({"conversationId", "id"})
        private String conversationId;
        private String status;
        private String title;

        public String resolvedId() {
            return conversationId;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageSendRequest {
        private String message;
        @JsonProperty("source_action")
        @JsonAlias("sourceAction")
        private String sourceAction;
        @JsonProperty("source_label")
        @JsonAlias("sourceLabel")
        private String sourceLabel;
        private Map<String, Object> context;
        @JsonProperty("client_request_id")
        @JsonAlias("clientRequestId")
        private String clientRequestId;
        @JsonProperty("agent_id")
        @JsonAlias("agentId")
        private String agentId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageSubmission {
        @JsonProperty("conversation_id")
        @JsonAlias("conversationId")
        private String conversationId;
        @JsonProperty("message_id")
        @JsonAlias("messageId")
        private String messageId;
        @JsonProperty("task_id")
        @JsonAlias("taskId")
        private String taskId;
        @JsonProperty("request_id")
        @JsonAlias("requestId")
        private String requestId;
        private String status;
        private Boolean idempotent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskResponse {
        @JsonProperty("task_id")
        @JsonAlias("taskId")
        private String taskId;
        private String status;
        private String objective;
        private String result;
        private String agent;
        private String provider;
        private String model;
        @JsonProperty("conversation_id")
        @JsonAlias("conversationId")
        private String conversationId;
        @JsonProperty("message_id")
        @JsonAlias("messageId")
        private String messageId;
        @JsonProperty("request_id")
        @JsonAlias("requestId")
        private String requestId;
        @JsonProperty("client_request_id")
        @JsonAlias("clientRequestId")
        private String clientRequestId;
        @JsonProperty("source_action")
        @JsonAlias("sourceAction")
        private String sourceAction;
        @JsonProperty("source_label")
        @JsonAlias("sourceLabel")
        private String sourceLabel;
        private String error;
        private Boolean idempotent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskEvent {
        private String type;
        @JsonProperty("task_id")
        @JsonAlias("taskId")
        private String taskId;
        private Map<String, Object> data;

        public String resolvedStatus() {
            if (data != null && data.get("status") != null) {
                return String.valueOf(data.get("status"));
            }
            return type;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConversationMessage {
        @JsonProperty("message_id")
        @JsonAlias("messageId")
        private String messageId;
        private String role;
        private String content;
        @JsonProperty("created_at")
        @JsonAlias("createdAt")
        private String createdAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallbackResponse {
        @JsonProperty("request_id")
        private String requestId;
        private boolean success;
        private Object result;
        private ToolCallbackError error;

        public static ToolCallbackResponse ok(String requestId, Object result) {
            ToolCallbackResponse r = new ToolCallbackResponse();
            r.requestId = requestId;
            r.success = true;
            r.result = result;
            return r;
        }

        public static ToolCallbackResponse error(String requestId, String code, String message) {
            ToolCallbackResponse r = new ToolCallbackResponse();
            r.requestId = requestId;
            r.success = false;
            ToolCallbackError err = new ToolCallbackError();
            err.code = code;
            err.message = message;
            r.error = err;
            return r;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallbackError {
        private String code;
        private String message;
    }
}
