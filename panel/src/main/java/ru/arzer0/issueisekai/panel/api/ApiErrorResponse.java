package ru.arzer0.issueisekai.panel.api;

import java.util.List;

public record ApiErrorResponse(String code, String message, List<Object> args) {
    public static ApiErrorResponse of(String code, Exception exception) {
        return new ApiErrorResponse(code, exception.getMessage(), List.of());
    }
}
