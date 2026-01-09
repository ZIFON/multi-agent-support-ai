package com.example.multiagent.orchestrator;

import com.example.multiagent.llm.LlmClient;
import com.example.multiagent.llm.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Router {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public Router(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public RouteResult route(List<Message> history, String userMessage) {
        String prompt = buildRoutingPrompt(history, userMessage);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", "You are a routing assistant. Classify user messages into TECH, BILLING, or OUT_OF_SCOPE. Respond with valid JSON only: {\"route\":\"TECH|BILLING|OUT_OF_SCOPE\",\"why\":\"brief explanation\"}"));
        messages.add(new Message("user", prompt));

        try {
            String response = llmClient.chatCompletion(messages);
            return parseRouteResponse(response);
        } catch (Exception e) {
            // Retry once with stricter instruction
            try {
                messages.set(messages.size() - 1, new Message("user", prompt + "\n\nIMPORTANT: Return ONLY valid JSON, no other text. Format: {\"route\":\"TECH|BILLING|OUT_OF_SCOPE\",\"why\":\"...\"}"));
                String response = llmClient.chatCompletion(messages);
                return parseRouteResponse(response);
            } catch (Exception e2) {
                // Default to OUT_OF_SCOPE if parsing fails
                return new RouteResult("OUT_OF_SCOPE", "Failed to parse routing response: " + e2.getMessage());
            }
        }
    }

    private String buildRoutingPrompt(List<Message> history, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Classify the following user message:\n\n");
        prompt.append("User message: ").append(userMessage).append("\n\n");
        
        if (!history.isEmpty()) {
            prompt.append("Recent conversation context:\n");
            int contextSize = Math.min(history.size(), 6);
            for (int i = Math.max(0, history.size() - contextSize); i < history.size(); i++) {
                Message msg = history.get(i);
                prompt.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Classification rules:\n");
        prompt.append("- TECH: Technical questions about APIs, integration, webhooks, authentication, errors, implementation\n");
        prompt.append("- BILLING: Questions about payments, refunds, subscriptions, plans, invoices, billing issues\n");
        prompt.append("- OUT_OF_SCOPE: Anything else (general questions, unrelated topics, etc.)\n\n");
        prompt.append("Respond with JSON only: {\"route\":\"TECH|BILLING|OUT_OF_SCOPE\",\"why\":\"brief explanation\"}");
        
        return prompt.toString();
    }

    private RouteResult parseRouteResponse(String response) {
        try {
            // Try to extract JSON from response (in case LLM adds extra text)
            String jsonStr = response.trim();
            int startIdx = jsonStr.indexOf("{");
            int endIdx = jsonStr.lastIndexOf("}");
            if (startIdx >= 0 && endIdx > startIdx) {
                jsonStr = jsonStr.substring(startIdx, endIdx + 1);
            }

            JsonNode json = objectMapper.readTree(jsonStr);
            String route = json.has("route") ? json.get("route").asText().toUpperCase() : "OUT_OF_SCOPE";
            String why = json.has("why") ? json.get("why").asText() : "No explanation provided";

            // Validate route
            if (!route.equals("TECH") && !route.equals("BILLING") && !route.equals("OUT_OF_SCOPE")) {
                route = "OUT_OF_SCOPE";
            }

            return new RouteResult(route, why);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse route response: " + response, e);
        }
    }
}
