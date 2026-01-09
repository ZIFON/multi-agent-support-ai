package com.example.multiagent.storage;

import java.time.LocalDateTime;

public class RefundCase {
    private String caseId;
    private String email;
    private String orderId;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
    private String formLink;

    public RefundCase() {
        this.createdAt = LocalDateTime.now();
        this.status = "OPEN";
    }

    public RefundCase(String caseId, String email, String orderId, String reason, String formLink) {
        this.caseId = caseId;
        this.email = email;
        this.orderId = orderId;
        this.reason = reason;
        this.formLink = formLink;
        this.createdAt = LocalDateTime.now();
        this.status = "OPEN";
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormLink() {
        return formLink;
    }

    public void setFormLink(String formLink) {
        this.formLink = formLink;
    }
}
