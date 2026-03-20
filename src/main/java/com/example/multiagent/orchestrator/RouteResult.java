package com.example.multiagent.orchestrator;

public class RouteResult {
    private String route; // "TECH", "BILLING", "OUT_OF_SCOPE"
    private String why;

    public RouteResult() {
    }

    public RouteResult(String route, String why) {
        this.route = route;
        this.why = why;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getWhy() {
        return why;
    }

    public void setWhy(String why) {
        this.why = why;
    }
}
