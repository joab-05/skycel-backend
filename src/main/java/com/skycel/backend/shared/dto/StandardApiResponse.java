package com.skycel.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Oculta campos null en la respuesta JSON
public class StandardApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String path;
    private Object metadata; // Para paginación, totales, etc.

    //Devuelve peticiones de creacion el puerto 201
    public static <T> StandardApiResponse<T> created(T data, String entityName) {
        return StandardApiResponse.<T>builder()
                .success(true)
                .message("Se crea "+entityName + " exitosamente")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    //Devuelve peticiones de actualizacion el puerto 200
    public static <T> StandardApiResponse<T> updated(T data, String entityName) {
        return StandardApiResponse.<T>builder()
                .success(true)
                .message("Se actualiza "+entityName+" exitosamente")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    //Devuelve peticiones de eliminacion por el puerto 204
    public static <T> StandardApiResponse<T> deleted(String entityName) {
        return StandardApiResponse.<T>builder()
                .success(true)
                .message("Se elimina "+entityName + " exitosamente")
                .timestamp(LocalDateTime.now())
                .build();
    }
    //Devuelve peticiones get (visualizar datos de pocos registros) por el puerto 200
    public static <T> StandardApiResponse<T> success(T data, String entityName) {
        return StandardApiResponse.<T>builder()
                .success(true)
                .message("Se obtienen "+entityName+ " exitosamemente")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    //Devuelve mensajes de error especificos si existen al llamar una peticion de cualquier tipo (GET, POST, PUT O DELETE)
    public static <T> StandardApiResponse<T> error(String message) {
        return StandardApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Para respuestas paginadas
    public static <T> StandardApiResponse<T> withPagination(T data, int page, int size, long total) {
        return StandardApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .metadata(new PaginationMeta(page, size, total))
                .build();
    }

    @Getter
    @AllArgsConstructor
    public static class PaginationMeta {
        private int page;
        private int size;
        private long total;
        private int totalPages;

        public PaginationMeta(int page, int size, long total) {
            this.page = page;
            this.size = size;
            this.total = total;
            this.totalPages = (int) Math.ceil((double) total / size);
        }
    }
}