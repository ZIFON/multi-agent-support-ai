package com.example.multiagent.storage;

import com.example.multiagent.llm.Message;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConversationStore {
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    public List<Message> getHistory(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public void append(String conversationId, Message message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
    }

    public void append(String conversationId, List<Message> messages) {
        List<Message> history = conversations.computeIfAbsent(conversationId, k -> new ArrayList<>());
        history.addAll(messages);
    }

    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }

    public List<Message> getHistoryForLlm(String conversationId) {
        List<Message> history = getHistory(conversationId);
        List<Message> result = new ArrayList<>();
        for (Message msg : history) {
            if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole()) || "system".equals(msg.getRole())) {
                result.add(msg);
            }
        }
        return result;
    }
}
