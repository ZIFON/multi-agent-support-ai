package com.example.multiagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {
    private final OpenAiClient openAiClient;

    public LlmClient() {
        this.openAiClient = new OpenAiClient();
    }

    public String chatCompletion(List<Message> messages) {
        List<Map<String, String>> apiMessages = convertMessages(messages);
        OpenAiClient.ChatCompletionResponse response = openAiClient.chatCompletion(apiMessages, null);
        return response.getContent();
    }

    public ChatCompletionResult chatCompletionWithTools(List<Message> messages, List<OpenAiClient.ToolDefinition> tools) {
        List<Map<String, String>> apiMessages = convertMessages(messages);
        OpenAiClient.ChatCompletionResponse response = openAiClient.chatCompletion(apiMessages, tools);
        
        ChatCompletionResult result = new ChatCompletionResult();
        result.setContent(response.getContent());
        result.setToolCalls(response.getToolCalls());
        return result;
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, String> apiMsg = new HashMap<>();
            apiMsg.put("role", msg.getRole());
            apiMsg.put("content", msg.getContent());
            result.add(apiMsg);
        }
        return result;
    }

    public static class ChatCompletionResult {
        private String content;
        private List<OpenAiClient.ToolCall> toolCalls = new ArrayList<>();

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<OpenAiClient.ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<OpenAiClient.ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public static OpenAiClient.ToolDefinition createToolDefinition(String name, String description, Map<String, Object> parameters) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();
        
        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            String paramName = param.getKey();
            @SuppressWarnings("unchecked")
            Map<String, String> paramDef = (Map<String, String>) param.getValue();
            
            ObjectNode prop = mapper.createObjectNode();
            prop.put("type", paramDef.get("type"));
            if (paramDef.containsKey("description")) {
                prop.put("description", paramDef.get("description"));
            }
            properties.set(paramName, prop);
            
            if (paramDef.containsKey("required") && Boolean.parseBoolean(paramDef.get("required"))) {
                required.add(paramName);
            }
        }
        
        schema.set("properties", properties);
        schema.set("required", required);
        
        return new OpenAiClient.ToolDefinition(name, description, schema);
    }
}
