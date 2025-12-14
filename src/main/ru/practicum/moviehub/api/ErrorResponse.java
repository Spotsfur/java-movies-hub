package ru.practicum.moviehub.api;

import java.util.List;

public class ErrorResponse {
    private final String reason;
    private final List<String> details;

    public ErrorResponse(String reason, List<String> details) {
        this.reason = reason;
        this.details = details;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getDetails() {
        return details;
    }
}