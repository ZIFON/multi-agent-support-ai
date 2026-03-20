package com.example.multiagent.storage;

import java.time.LocalDate;

public class PlanInfo {
    private String planName;
    private Double price;
    private LocalDate renewalDate;

    public PlanInfo() {
    }

    public PlanInfo(String planName, Double price, LocalDate renewalDate) {
        this.planName = planName;
        this.price = price;
        this.renewalDate = renewalDate;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public void setRenewalDate(LocalDate renewalDate) {
        this.renewalDate = renewalDate;
    }
}
