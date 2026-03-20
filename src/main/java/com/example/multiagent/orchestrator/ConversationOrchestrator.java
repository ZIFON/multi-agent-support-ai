package com.example.multiagent.orchestrator;

import com.example.multiagent.agents.BillingAgent;
import com.example.multiagent.agents.TechAgent;
import com.example.multiagent.controller.ChatResponse;
import com.example.multiagent.llm.Message;
import com.example.multiagent.retrieval.Retriever;
import com.example.multiagent.storage.InMemoryConversationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ConversationOrchestrator {
    private final Router router;
    private final Retriever retriever;
    private final TechAgent techAgent;
    private final BillingAgent billingAgent;
    private final InMemoryConversationStore conversationStore;

    @Autowired
    public ConversationOrchestrator(
            Router router,
            Retriever retriever,
            TechAgent techAgent,
            BillingAgent billingAgent,
            InMemoryConversationStore conversationStore) {
        this.router = router;
        this.retriever = retriever;
        this.techAgent = techAgent;
        this.billingAgent = billingAgent;
        this.conversationStore = conversationStore;
    }

    public ChatResponse handle(String conversationId, String message) {
        // Get conversation history
        List<Message> history = conversationStore.getHistoryForLlm(conversationId);

        // Save user message
        Message userMessage = new Message("user", message);
        conversationStore.append(conversationId, userMessage);

        // Route the message
        RouteResult routeResult = router.route(history, message);
        String route = routeResult.getRoute();

        ChatResponse response;

        switch (route) {
            case "TECH":
                response = handleTechRequest(history, message);
                break;
            case "BILLING":
                response = handleBillingRequest(history, message);
                break;
            case "OUT_OF_SCOPE":
            default:
                response = handleOutOfScope();
                break;
        }

        response.setConversationId(conversationId);
        response.setAgent(route);

        // Save assistant response
        Message assistantMessage = new Message("assistant", response.getResponse());
        conversationStore.append(conversationId, assistantMessage);

        return response;
    }

    private ChatResponse handleTechRequest(List<Message> history, String message) {
        // Retrieve relevant snippets
        List<com.example.multiagent.retrieval.Chunk> snippets = retriever.retrieve(message, 4);

        // Get answer from TechAgent
        TechAgent.TechAgentResult result = techAgent.answer(history, message, snippets);

        ChatResponse response = new ChatResponse();
        response.setResponse(result.getAnswer());
        response.setCitations(result.getCitations().toArray(new String[0]));
        response.setToolUsed(null);

        Map<String, Object> meta = new HashMap<>();
        meta.put("snippetsFound", snippets.size());
        meta.put("needsClarification", result.needsClarification());
        response.setMeta(meta);

        return response;
    }

    private ChatResponse handleBillingRequest(List<Message> history, String message) {
        // Get answer from BillingAgent (handles tool calling internally)
        BillingAgent.BillingAgentResult result = billingAgent.answer(history, message);

        ChatResponse response = new ChatResponse();
        response.setResponse(result.getResponse());
        response.setToolUsed(result.getToolUsed());
        response.setCitations(null);

        Map<String, Object> meta = new HashMap<>(result.getMeta());
        if (result.getToolUsed() != null && meta.containsKey("caseId")) {
            response.setCaseId((String) meta.get("caseId"));
        }
        response.setMeta(meta);

        return response;
    }

    private ChatResponse handleOutOfScope() {
        ChatResponse response = new ChatResponse();
        response.setResponse(
            "I apologize, but I'm specifically trained to help with technical questions and billing inquiries. " +
            "Could you please rephrase your question, or let me know if you have a technical or billing-related question I can help with?"
        );
        response.setCitations(null);
        response.setToolUsed(null);
        return response;
    }
}
