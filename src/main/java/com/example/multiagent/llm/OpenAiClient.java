package com.example.multiagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required");
        }
    }

    public ChatCompletionResponse chatCompletion(List<Map<String, String>> messages, List<ToolDefinition> tools) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    private ChatCompletionResponse parseResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            throw new RuntimeException("Invalid response from OpenAI API");
        }

        JsonNode choice = choices.get(0);
        JsonNode message = choice.get("message");
        
        String content = message.has("content") && !message.get("content").isNull() 
                ? message.get("content").asText() 
                : null;
        
        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            for (JsonNode toolCallNode : message.get("tool_calls")) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId(toolCallNode.get("id").asText());
                toolCall.setType(toolCallNode.get("type").asText());
                
                JsonNode function = toolCallNode.get("function");
                toolCall.setFunctionName(function.get("name").asText());
                toolCall.setArguments(function.get("arguments").asText());
                toolCalls.add(toolCall);
            }
        }

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setContent(content);
        response.setToolCalls(toolCalls);
        return response;
    }

    public static class ChatCompletionResponse {
        private String content;
        private List<ToolCall> toolCalls = new ArrayList<>();

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public static class ToolCall {
        private String id;
        private String type;
        private String functionName;
        private String arguments;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }

    public static class ToolDefinition {
        private String type = "function";
        private FunctionDefinition function;

        public ToolDefinition(String name, String description, JsonNode parameters) {
            this.function = new FunctionDefinition(name, description, parameters);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FunctionDefinition getFunction() {
            return function;
        }

        public void setFunction(FunctionDefinition function) {
            this.function = function;
        }
    }

    public static class FunctionDefinition {
        private String name;
        private String description;
        private JsonNode parameters;

        public FunctionDefinition(String name, String description, JsonNode parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public JsonNode getParameters() {
            return parameters;
        }

        public void setParameters(JsonNode parameters) {
            this.parameters = parameters;
        }
    }
}
