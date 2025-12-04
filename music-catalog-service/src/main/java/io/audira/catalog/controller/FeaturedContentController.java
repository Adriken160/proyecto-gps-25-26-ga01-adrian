package io.audira.catalog.controller;

import io.audira.catalog.dto.FeaturedContentRequest;
import io.audira.catalog.dto.FeaturedContentResponse;
import io.audira.catalog.dto.ReorderRequest;
import io.audira.catalog.service.FeaturedContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión de contenido destacado (carruseles y banners).
 * <p>
 * Permite a los administradores seleccionar qué contenido aparece en la página principal.
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class FeaturedContentController {

    private static final String ERROR_KEY = "error";
    private final FeaturedContentService featuredContentService;

    /**
     * @return Lista completa de destacados (activos e inactivos).
     */
    @GetMapping("/api/featured-content")
    public ResponseEntity<List<FeaturedContentResponse>> getAllFeaturedContent() {
        return ResponseEntity.ok(featuredContentService.getAllFeaturedContent());
    }

    /**
     * @return Lista de destacados activos.
     */
    @GetMapping("/api/featured-content/active")
    public ResponseEntity<List<FeaturedContentResponse>> getActiveFeaturedContent() {
        return ResponseEntity.ok(featuredContentService.getActiveFeaturedContent());
    }

    /**
     * Obtiene un elemento destacado por su ID.
     *
     * @param id ID del registro.
     * @return El contenido destacado correspondiente.
     */
    @GetMapping("/api/featured-content/{id}")
    public ResponseEntity<Object> getFeaturedContentById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(featuredContentService.getFeaturedContentById(id));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Crea un nuevo elemento destacado.
     *
     * @param request DTO con los datos del contenido a destacar (ID, tipo, fechas).
     * @return El contenido destacado creado.
     */
    @PostMapping("/api/featured-content")
    public ResponseEntity<Object> createFeaturedContent(@RequestBody FeaturedContentRequest request) {
        try {
            FeaturedContentResponse response = featuredContentService.createFeaturedContent(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Actualiza un elemento destacado existente.
     *
     * @param id ID del registro de destacado.
     * @param request Datos a actualizar.
     * @return El elemento actualizado.
     */
    @PutMapping("/api/featured-content/{id}")
    public ResponseEntity<Object> updateFeaturedContent(
            @PathVariable Long id,
            @RequestBody FeaturedContentRequest request) {
        try {
            return ResponseEntity.ok(featuredContentService.updateFeaturedContent(id, request));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Elimina un elemento de la lista de destacados.
     *
     * @param id ID del registro.
     * @return 204 No Content.
     */
    @DeleteMapping("/api/featured-content/{id}")
    public ResponseEntity<Object> deleteFeaturedContent(@PathVariable Long id) {
        try {
            featuredContentService.deleteFeaturedContent(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Reordena la lista de contenidos destacados.
     *
     * @param request DTO que contiene el nuevo orden de los IDs.
     * @return Lista reordenada.
     */
    @PutMapping("/api/featured-content/reorder")
    public ResponseEntity<Object> reorderFeaturedContent(@RequestBody ReorderRequest request) {
        try {
            return ResponseEntity.ok(featuredContentService.reorderFeaturedContent(request));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Activa o desactiva rápidamente un contenido destacado.
     *
     * @param id ID del registro.
     * @param body Mapa con la clave "isActive" (booleano).
     * @return El elemento actualizado.
     */
    @PatchMapping("/api/featured-content/{id}/toggle-active")
    public ResponseEntity<Object> toggleActive(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        try {
            Boolean isActive = body.get("isActive");
            if (isActive == null) {
                Map<String, String> error = new HashMap<>();
                error.put(ERROR_KEY, "isActive field is required");
                return ResponseEntity.badRequest().body(error);
            }
            return ResponseEntity.ok(featuredContentService.toggleActive(id, isActive));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}
