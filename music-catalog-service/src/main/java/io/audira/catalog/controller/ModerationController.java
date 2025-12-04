package io.audira.catalog.controller;

import io.audira.catalog.dto.ModerationHistoryResponse;
import io.audira.catalog.dto.ModerationRequest;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.ModerationHistory;
import io.audira.catalog.model.ModerationStatus;
import io.audira.catalog.model.Song;
import io.audira.catalog.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la moderación de contenido (GA01-162, GA01-163).
 * <p>
 * Permite a los administradores revisar, aprobar o rechazar canciones y álbumes subidos por artistas.
 * </p>
 */
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private static final String ERROR_KEY = "error";
    private final ModerationService moderationService;

    /**
     * Aprueba una canción pendiente de revisión.
     *
     * @param id ID de la canción.
     * @param request Datos de la moderación (notas, moderador).
     * @return La canción aprobada.
     */
    @PostMapping("/songs/{id}/approve")
    public ResponseEntity<Object> approveSong(@PathVariable Long id,
                                         @RequestBody ModerationRequest request) {
        try {
            Song song = moderationService.approveSong(id, request.getAdminId(), request.getNotes());
            return ResponseEntity.ok(song);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Rechaza una canción indicando el motivo.
     *
     * @param id ID de la canción.
     * @param request Datos del rechazo (razón obligatoria).
     * @return La canción rechazada.
     */
    @PostMapping("/songs/{id}/reject")
    public ResponseEntity<Object> rejectSong(@PathVariable Long id,
                                        @RequestBody ModerationRequest request) {
        try {
            Song song = moderationService.rejectSong(id, request.getAdminId(),
                    request.getRejectionReason(), request.getNotes());
            return ResponseEntity.ok(song);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Aprueba un álbum completo.
     *
     * @param id ID del álbum.
     * @param request Datos de moderación.
     * @return El álbum aprobado.
     */
    @PostMapping("/albums/{id}/approve")
    public ResponseEntity<Object> approveAlbum(@PathVariable Long id,
                                          @RequestBody ModerationRequest request) {
        try {
            Album album = moderationService.approveAlbum(id, request.getAdminId(), request.getNotes());
            return ResponseEntity.ok(album);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Rechaza un álbum completo.
     *
     * @param id ID del álbum.
     * @param request Datos del rechazo.
     * @return El álbum rechazado.
     */
    @PostMapping("/albums/{id}/reject")
    public ResponseEntity<Object> rejectAlbum(@PathVariable Long id,
                                         @RequestBody ModerationRequest request) {
        try {
            Album album = moderationService.rejectAlbum(id, request.getAdminId(),
                    request.getRejectionReason(), request.getNotes());
            return ResponseEntity.ok(album);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Obtiene la cola de canciones pendientes de moderación.
     * @return Lista de canciones en estado PENDING_REVIEW.
     */
    @GetMapping("/songs/pending")
    public ResponseEntity<List<Song>> getPendingSongs() {
        return ResponseEntity.ok(moderationService.getPendingSongs());
    }

    /**
     * Obtiene la cola de álbumes pendientes de moderación.
     * @return Lista de álbumes en estado PENDING_REVIEW.
     */
    @GetMapping("/albums/pending")
    public ResponseEntity<List<Album>> getPendingAlbums() {
        return ResponseEntity.ok(moderationService.getPendingAlbums());
    }

    /**
     * Obtiene canciones filtradas por estado de moderación.
     *
     * @param status Estado de moderación (PENDING_REVIEW, APPROVED, REJECTED).
     * @return Lista de canciones con el estado especificado.
     */
    @GetMapping("/songs/status/{status}")
    public ResponseEntity<List<Song>> getSongsByStatus(@PathVariable String status) {
        try {
            ModerationStatus moderationStatus = ModerationStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(moderationService.getSongsByStatus(moderationStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtiene álbumes filtrados por estado de moderación.
     *
     * @param status Estado de moderación (PENDING_REVIEW, APPROVED, REJECTED).
     * @return Lista de álbumes con el estado especificado.
     */
    @GetMapping("/albums/status/{status}")
    public ResponseEntity<List<Album>> getAlbumsByStatus(@PathVariable String status) {
        try {
            ModerationStatus moderationStatus = ModerationStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(moderationService.getAlbumsByStatus(moderationStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Consulta el historial completo de moderaciones.
     *
     * @return Lista histórica de aprobaciones y rechazos.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ModerationHistoryResponse>> getModerationHistory() {
        List<ModerationHistory> history = moderationService.getModerationHistory();
        List<ModerationHistoryResponse> response = history.stream()
                .map(this::toResponse)
                .toList(); 
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta el historial de moderaciones de un producto específico (canción o álbum).
     *
     * @param productType Tipo de producto ("song" o "album").
     * @param productId   ID del producto.
     * @return Lista histórica de aprobaciones y rechazos.
     */
    @GetMapping("/history/{productType}/{productId}")
    public ResponseEntity<List<ModerationHistoryResponse>> getProductHistory(
            @PathVariable String productType,
            @PathVariable Long productId) {
        List<ModerationHistory> history = moderationService.getProductModerationHistory(
                productId, productType.toUpperCase());
        List<ModerationHistoryResponse> response = history.stream()
                .map(this::toResponse)
                .toList(); 
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta el historial de moderaciones de un artista específico.
     *
     * @param artistId ID del artista.
     * @return Lista histórica de aprobaciones y rechazos.
     */
    @GetMapping("/history/artist/{artistId}")
    public ResponseEntity<List<ModerationHistoryResponse>> getArtistHistory(
            @PathVariable Long artistId) {
        List<ModerationHistory> history = moderationService.getArtistModerationHistory(artistId);
        List<ModerationHistoryResponse> response = history.stream()
                .map(this::toResponse)
                .toList(); 
        return ResponseEntity.ok(response);
    }

    private ModerationHistoryResponse toResponse(ModerationHistory history) {
        return ModerationHistoryResponse.builder()
                .id(history.getId())
                .productId(history.getProductId())
                .productType(history.getProductType())
                .productTitle(history.getProductTitle())
                .artistId(history.getArtistId())
                .artistName(history.getArtistName())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .moderatedBy(history.getModeratedBy())
                .moderatorName(history.getModeratorName())
                .rejectionReason(history.getRejectionReason())
                .moderatedAt(history.getModeratedAt())
                .notes(history.getNotes())
                .build();
    }
}
