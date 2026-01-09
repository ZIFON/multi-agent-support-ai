package com.example.multiagent.agents;

import com.example.multiagent.llm.LlmClient;
import com.example.multiagent.llm.Message;
import com.example.multiagent.retrieval.Chunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TechAgent {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TechAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public TechAgentResult answer(List<Message> history, String userMessage, List<Chunk> snippets) {
        List<Message> messages = buildMessages(history, userMessage, snippets);
        
        try {
            String response = llmClient.chatCompletion(messages);
            return parseResponse(response, snippets);
        } catch (Exception e) {
            // Fallback: return plain text response
            return new TechAgentResult(
                "I apologize, but I encountered an error processing your question. " +
                (snippets.isEmpty() ? "No documentation was found to answer your question." : ""),
                new ArrayList<>(),
                snippets.isEmpty()
            );
        }
    }

    private List<Message> buildMessages(List<Message> history, String userMessage, List<Chunk> snippets) {
        List<Message> messages = new ArrayList<>();
        
        String systemPrompt = "You are a Technical Specialist. Answer questions ONLY using the provided documentation snippets. " +
                "If the answer is not present in the documentation, explicitly state that the docs do not cover this topic and ask a clarifying question. " +
                "Do NOT guess or make up information. Always include citations in the format [docId:sectionTitle] for each snippet you use. " +
                "Respond with valid JSON only in this format: {\"answer\":\"your answer\",\"citations\":[\"docId:sectionTitle\",...],\"needs_clarification\":true|false}";
        
        messages.add(new Message("system", systemPrompt));

        // Add recent conversation history
        int historySize = Math.min(history.size(), 8);
        for (int i = Math.max(0, history.size() - historySize); i < history.size(); i++) {
            messages.add(history.get(i));
        }

        // Build user message with snippets
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("User question: ").append(userMessage).append("\n\n");
        
        if (snippets.isEmpty()) {
            userPrompt.append("No relevant documentation snippets were found for this question.\n");
        } else {
            userPrompt.append("Relevant documentation snippets:\n\n");
            for (int i = 0; i < snippets.size(); i++) {
                Chunk chunk = snippets.get(i);
                userPrompt.append("Snippet ").append(i + 1).append(" [").append(chunk.getDocId()).append(":")
                          .append(chunk.getSectionTitle()).append("]:\n");
                userPrompt.append(chunk.getText()).append("\n\n");
            }
        }
        
        userPrompt.append("Respond with JSON: {\"answer\":\"...\",\"citations\":[\"docId:sectionTitle\",...],\"needs_clarification\":true|false}");
        
        messages.add(new Message("user", userPrompt.toString()));
        return messages;
    }

    private TechAgentResult parseResponse(String response, List<Chunk> snippets) {
        try {
            // Try to extract JSON
            String jsonStr = response.trim();
            int startIdx = jsonStr.indexOf("{");
            int endIdx = jsonStr.lastIndexOf("}");
            
            if (startIdx >= 0 && endIdx > startIdx) {
                jsonStr = jsonStr.substring(startIdx, endIdx + 1);
            }

            JsonNode json = objectMapper.readTree(jsonStr);
            
            String answer = json.has("answer") ? json.get("answer").asText() : response;
            List<String> citations = new ArrayList<>();
            
            if (json.has("citations") && json.get("citations").isArray()) {
                for (JsonNode citation : json.get("citations")) {
                    citations.add(citation.asText());
                }
            }
            
            boolean needsClarification = json.has("needs_clarification") && json.get("needs_clarification").asBoolean(false);
            boolean docsCoverIt = !snippets.isEmpty() && !needsClarification;
            
            return new TechAgentResult(answer, citations, needsClarification || snippets.isEmpty());
        } catch (Exception e) {
            // Fallback: check if response mentions docs don't cover it
            boolean docsCoverIt = !snippets.isEmpty() && 
                    !response.toLowerCase().contains("don't cover") && 
                    !response.toLowerCase().contains("doesn't cover") &&
                    !response.toLowerCase().contains("not found");
            
            return new TechAgentResult(response, new ArrayList<>(), !docsCoverIt);
        }
    }

    public static class TechAgentResult {
        private String answer;
        private List<String> citations;
        private boolean needsClarification;

        public TechAgentResult(String answer, List<String> citations, boolean needsClarification) {
            this.answer = answer;
            this.citations = citations;
            this.needsClarification = needsClarification;
        }

        public String getAnswer() {
            return answer;
        }

        public List<String> getCitations() {
            return citations;
        }

        public boolean needsClarification() {
            return needsClarification;
        }
    }
}
