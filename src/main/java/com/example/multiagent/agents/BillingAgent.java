package com.example.multiagent.agents;

import com.example.multiagent.llm.LlmClient;
import com.example.multiagent.llm.Message;
import com.example.multiagent.llm.OpenAiClient;
import com.example.multiagent.tools.BillingTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BillingAgent {
    private final LlmClient llmClient;
    private final BillingTools billingTools;
    private final ObjectMapper objectMapper;

    @Autowired
    public BillingAgent(LlmClient llmClient, BillingTools billingTools) {
        this.llmClient = llmClient;
        this.billingTools = billingTools;
        this.objectMapper = new ObjectMapper();
    }

    public BillingAgentResult answer(List<Message> history, String userMessage) {
        List<Message> messages = buildMessages(history, userMessage);
        List<OpenAiClient.ToolDefinition> tools = createToolDefinitions();

        int maxIterations = 5;
        String toolUsed = null;
        Map<String, Object> meta = new HashMap<>();

        for (int i = 0; i < maxIterations; i++) {
            LlmClient.ChatCompletionResult result = llmClient.chatCompletionWithTools(messages, tools);

            if (!result.hasToolCalls()) {
                // No tool calls, return the response
                return new BillingAgentResult(result.getContent(), toolUsed, meta);
            }

            // Execute tool calls
            for (OpenAiClient.ToolCall toolCall : result.getToolCalls()) {
                toolUsed = toolCall.getFunctionName();
                Map<String, Object> toolResult = executeTool(toolCall);
                meta.putAll(toolResult);

                // Add tool result to messages
                Message toolResultMessage = new Message("tool", buildToolResultMessage(toolCall.getId(), toolResult));
                messages.add(new Message("assistant", null)); // Placeholder for tool call
                messages.add(toolResultMessage);
            }
        }

        // If we've exhausted iterations, return last response
        return new BillingAgentResult("I've processed your request. Please let me know if you need anything else.", toolUsed, meta);
    }

    private List<Message> buildMessages(List<Message> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        
        String systemPrompt = "You are a Billing Support Agent. Help users with billing questions, refunds, subscriptions, and plans. " +
                "Ask for missing information (email, orderId, purchaseDate, paymentMethod) when needed. " +
                "Use the provided tools to look up information and process refunds. " +
                "Be friendly, professional, and clear.";
        
        messages.add(new Message("system", systemPrompt));

        // Add conversation history (last 10 messages for context)
        int historySize = Math.min(history.size(), 10);
        for (int i = Math.max(0, history.size() - historySize); i < history.size(); i++) {
            messages.add(history.get(i));
        }

        messages.add(new Message("user", userMessage));
        return messages;
    }

    private List<OpenAiClient.ToolDefinition> createToolDefinitions() {
        List<OpenAiClient.ToolDefinition> tools = new ArrayList<>();

        // Tool 1: openRefundCase
        Map<String, Object> openRefundParams = new HashMap<>();
        openRefundParams.put("email", Map.of("type", "string", "description", "Customer email address", "required", "true"));
        openRefundParams.put("orderId", Map.of("type", "string", "description", "Order ID for the refund", "required", "true"));
        openRefundParams.put("reason", Map.of("type", "string", "description", "Reason for refund request", "required", "true"));
        tools.add(LlmClient.createToolDefinition("openRefundCase", 
                "Opens a refund case and generates a form link. Requires email, orderId, and reason.", 
                openRefundParams));

        // Tool 2: getPlanInfo
        Map<String, Object> getPlanParams = new HashMap<>();
        getPlanParams.put("email", Map.of("type", "string", "description", "Customer email address", "required", "true"));
        tools.add(LlmClient.createToolDefinition("getPlanInfo", 
                "Retrieves subscription plan information for a customer by email address.", 
                getPlanParams));

        // Tool 3: estimateRefundTimeline
        Map<String, Object> estimateParams = new HashMap<>();
        estimateParams.put("paymentMethod", Map.of("type", "string", "description", "Payment method used (e.g., credit card, PayPal, bank transfer)", "required", "true"));
        estimateParams.put("purchaseDateIso", Map.of("type", "string", "description", "Purchase date in ISO format (YYYY-MM-DD)", "required", "true"));
        tools.add(LlmClient.createToolDefinition("estimateRefundTimeline", 
                "Estimates refund timeline based on payment method and purchase date according to billing policy.", 
                estimateParams));

        return tools;
    }

    private Map<String, Object> executeTool(OpenAiClient.ToolCall toolCall) {
        try {
            JsonNode args = objectMapper.readTree(toolCall.getArguments());
            String functionName = toolCall.getFunctionName();

            switch (functionName) {
                case "openRefundCase":
                    String email = args.get("email").asText();
                    String orderId = args.get("orderId").asText();
                    String reason = args.get("reason").asText();
                    return billingTools.openRefundCase(email, orderId, reason);

                case "getPlanInfo":
                    String email2 = args.get("email").asText();
                    return billingTools.getPlanInfo(email2);

                case "estimateRefundTimeline":
                    String paymentMethod = args.get("paymentMethod").asText();
                    String purchaseDate = args.get("purchaseDateIso").asText();
                    return billingTools.estimateRefundTimeline(paymentMethod, purchaseDate);

                default:
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Unknown tool: " + functionName);
                    return error;
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Tool execution failed: " + e.getMessage());
            return error;
        }
    }

    private String buildToolResultMessage(String toolCallId, Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"Failed to serialize result\"}";
        }
    }

    public static class BillingAgentResult {
        private String response;
        private String toolUsed;
        private Map<String, Object> meta;

        public BillingAgentResult(String response, String toolUsed, Map<String, Object> meta) {
            this.response = response;
            this.toolUsed = toolUsed;
            this.meta = meta;
        }

        public String getResponse() {
            return response;
        }

        public String getToolUsed() {
            return toolUsed;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }
    }
}
