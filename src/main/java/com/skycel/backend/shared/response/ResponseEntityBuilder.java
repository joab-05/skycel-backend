package com.skycel.backend.shared.response;

import com.skycel.backend.shared.dto.StandardApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseEntityBuilder {

    public static <T> ResponseEntity<StandardApiResponse<T>> created(T data, String entityName) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardApiResponse.created(data, entityName));
    }

    public static <T> ResponseEntity<StandardApiResponse<T>> ok(T data, String entityName) {
        return ResponseEntity.ok(StandardApiResponse.success(data, entityName));
    }

    public static <T> ResponseEntity<StandardApiResponse<T>> updated(T data, String entityName) {
        return ResponseEntity.ok(StandardApiResponse.updated(data, entityName));
    }

    public static <T> ResponseEntity<StandardApiResponse<T>> deleted(String entityName) {
        return ResponseEntity.ok(StandardApiResponse.deleted(entityName));
    }

    public static <T> ResponseEntity<StandardApiResponse<T>> noContent() {
        return ResponseEntity.noContent().build();
    }
}