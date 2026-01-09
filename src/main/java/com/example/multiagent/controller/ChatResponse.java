package com.example.multiagent.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    @JsonProperty("conversationId")
    private String conversationId;

    @JsonProperty("agent")
    private String agent;

    @JsonProperty("response")
    private String response;

    @JsonProperty("citations")
    private String[] citations;

    @JsonProperty("toolUsed")
    private String toolUsed;

    @JsonProperty("caseId")
    private String caseId;

    @JsonProperty("meta")
    private Map<String, Object> meta;

    public ChatResponse() {
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String[] getCitations() {
        return citations;
    }

    public void setCitations(String[] citations) {
        this.citations = citations;
    }

    public String getToolUsed() {
        return toolUsed;
    }

    public void setToolUsed(String toolUsed) {
        this.toolUsed = toolUsed;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
