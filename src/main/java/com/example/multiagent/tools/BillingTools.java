package com.example.multiagent.tools;

import com.example.multiagent.storage.InMemoryBillingStore;
import com.example.multiagent.storage.RefundCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class BillingTools {
    @Autowired
    private InMemoryBillingStore billingStore;

    public Map<String, Object> openRefundCase(String email, String orderId, String reason) {
        String formLink = "https://example.com/refund-form/" + UUID.randomUUID().toString();
        RefundCase refundCase = billingStore.createRefundCase(email, orderId, reason, formLink);

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", refundCase.getCaseId());
        result.put("formLink", refundCase.getFormLink());
        result.put("status", "OPEN");
        return result;
    }

    public Map<String, Object> getPlanInfo(String email) {
        com.example.multiagent.storage.PlanInfo planInfo = billingStore.getPlanInfo(email);
        Map<String, Object> result = new HashMap<>();
        
        if (planInfo == null) {
            result.put("error", "No plan found for email: " + email);
            return result;
        }

        result.put("email", email);
        result.put("planName", planInfo.getPlanName());
        result.put("price", planInfo.getPrice());
        result.put("renewalDate", planInfo.getRenewalDate().toString());
        return result;
    }

    public Map<String, Object> estimateRefundTimeline(String paymentMethod, String purchaseDateIso) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            LocalDate purchaseDate = LocalDate.parse(purchaseDateIso);
            LocalDate now = LocalDate.now();
            long daysSincePurchase = java.time.temporal.ChronoUnit.DAYS.between(purchaseDate, now);
            
            // Read billing policy
            String policy = readBillingPolicy();
            boolean eligible = daysSincePurchase <= 14;
            
            result.put("eligible", eligible);
            result.put("daysSincePurchase", daysSincePurchase);
            
            if (!eligible) {
                result.put("timelineText", "Refund not eligible: Purchase was " + daysSincePurchase + " days ago. Refund window is 14 days.");
                return result;
            }

            // Estimate timeline based on payment method
            String timelineText;
            String paymentMethodLower = paymentMethod.toLowerCase();
            
            if (paymentMethodLower.contains("card") || paymentMethodLower.contains("credit") || paymentMethodLower.contains("debit")) {
                timelineText = "Card refunds typically take 5-10 business days to process and appear on your statement.";
            } else if (paymentMethodLower.contains("paypal")) {
                timelineText = "PayPal refunds typically take 1-3 business days to process.";
            } else if (paymentMethodLower.contains("bank") || paymentMethodLower.contains("transfer")) {
                timelineText = "Bank transfer refunds may take up to 10 business days to process.";
            } else {
                timelineText = "Refund timeline depends on payment method. Please allow 5-10 business days for processing.";
            }
            
            result.put("timelineText", timelineText);
            result.put("policy", policy);
        } catch (Exception e) {
            result.put("error", "Invalid date format or error: " + e.getMessage());
            result.put("timelineText", "Unable to estimate timeline. Please provide valid purchase date in ISO format (YYYY-MM-DD).");
        }
        
        return result;
    }

    private String readBillingPolicy() {
        try {
            return Files.readString(Paths.get("./docs/billing_policy.md"));
        } catch (IOException e) {
            return "Refund window: 14 days from purchase date. Processing times vary by payment method.";
        }
    }
}
