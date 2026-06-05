package com.won.autoinvestor.pilot.service;

public class OrderPipelineResult {

    private final boolean accepted;
    private final String status;
    private final String message;

    private OrderPipelineResult(boolean accepted, String status, String message) {
        this.accepted = accepted;
        this.status = status;
        this.message = message;
    }

    public static OrderPipelineResult accepted(String message) {
        return new OrderPipelineResult(true, "ACCEPTED", message);
    }

    public static OrderPipelineResult rejected(String message) {
        return new OrderPipelineResult(false, "REJECTED", message);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
