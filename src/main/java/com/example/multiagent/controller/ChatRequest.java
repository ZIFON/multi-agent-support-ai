package com.example.multiagent.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatRequest {
    @JsonProperty("conversationId")
    private String conversationId;

    @JsonProperty("message")
    private String message;

    public ChatRequest() {
    }

    public ChatRequest(String conversationId, String message) {
        this.conversationId = conversationId;
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
