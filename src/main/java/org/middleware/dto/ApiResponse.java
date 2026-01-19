package org.middleware.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

// DTO pour les réponses standardisées
@RegisterForReflection
public class ApiResponse<T> {
    public boolean success;
    public String message;
    public T data;
    public String errorCode;
    public String errorDescription;
    public LocalDateTime timestamp;

    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.message = "Opération réussie";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = success(data);
        response.message = message;
        return response;
    }

    public static <T> ApiResponse<T> error(String errorCode, String errorDescription) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.errorCode = errorCode;
        response.errorDescription = errorDescription;
        response.message = "Une erreur est survenue";
        return response;
    }
}

// DTO pour les réponses DGI
