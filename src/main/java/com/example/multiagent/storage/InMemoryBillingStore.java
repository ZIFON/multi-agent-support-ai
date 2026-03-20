package com.example.multiagent.storage;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryBillingStore {
    private final Map<String, PlanInfo> plansByEmail = new ConcurrentHashMap<>();
    private final Map<String, RefundCase> casesById = new ConcurrentHashMap<>();
    private int nextCaseId = 1000;

    public InMemoryBillingStore() {
        // Seed data for demo
        plansByEmail.put("user1@example.com", new PlanInfo("Premium", 29.99, LocalDate.now().plusMonths(1)));
        plansByEmail.put("user2@example.com", new PlanInfo("Basic", 9.99, LocalDate.now().plusDays(15)));
        plansByEmail.put("user3@example.com", new PlanInfo("Enterprise", 99.99, LocalDate.now().plusMonths(3)));
    }

    public PlanInfo getPlanInfo(String email) {
        return plansByEmail.get(email);
    }

    public void savePlanInfo(String email, PlanInfo planInfo) {
        plansByEmail.put(email, planInfo);
    }

    public RefundCase getRefundCase(String caseId) {
        return casesById.get(caseId);
    }

    public RefundCase createRefundCase(String email, String orderId, String reason, String formLink) {
        String caseId = "REF-" + (nextCaseId++);
        RefundCase refundCase = new RefundCase(caseId, email, orderId, reason, formLink);
        casesById.put(caseId, refundCase);
        return refundCase;
    }

    public List<RefundCase> getRefundCasesByEmail(String email) {
        List<RefundCase> result = new ArrayList<>();
        for (RefundCase refundCase : casesById.values()) {
            if (email.equals(refundCase.getEmail())) {
                result.add(refundCase);
            }
        }
        return result;
    }
}
