package com.example.multiagent.controller;

import com.example.multiagent.orchestrator.ConversationOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatController {
    private final ConversationOrchestrator orchestrator;

    @Autowired
    public ChatController(ConversationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            if (request.getConversationId() == null || request.getMessage() == null || 
                request.getConversationId().trim().isEmpty() || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            ChatResponse response = orchestrator.handle(request.getConversationId(), request.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setAgent("ERROR");
            errorResponse.setResponse("An error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
