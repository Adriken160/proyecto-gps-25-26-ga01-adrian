package io.audira.catalog.service;

import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.UserDTO;
import io.audira.catalog.model.*;
import io.audira.catalog.repository.AlbumRepository;
import io.audira.catalog.repository.ModerationHistoryRepository;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GA01-162 y GA01-163: Servicio de moderación de contenido
 * Gestiona aprobación/rechazo de canciones y álbumes, y mantiene historial
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ModerationHistoryRepository moderationHistoryRepository;
    private final UserServiceClient userServiceClient;
    private final io.audira.catalog.client.NotificationClient notificationClient;
    private static final String WARNING_SONG_KEY = "Canción no encontrada: ";
    private static final String WARNING_ALBUM_KEY = "Álbum no encontrado: ";

    /**
     * GA01-162: Aprobar una canción
     * Al aprobar, la canción se publica automáticamente
     */
    @Transactional
    public Song approveSong(Long songId, Long adminId, String notes) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_SONG_KEY + songId));

        ModerationStatus previousStatus = song.getModerationStatus();

        // Actualizar estado de moderación
        song.setModerationStatus(ModerationStatus.APPROVED);
        song.setModeratedBy(adminId);
        song.setModeratedAt(LocalDateTime.now());
        song.setRejectionReason(null); // Limpiar razón de rechazo si existía
        song.setPublished(true); // Publicar automáticamente al aprobar

        Song savedSong = songRepository.save(song);

        // Registrar en historial
        recordModerationHistory(savedSong, previousStatus, ModerationStatus.APPROVED,
                adminId, null, notes);

        // Notificar al artista que su canción fue aprobada
        try {
            notificationClient.notifyArtistApproved(savedSong.getArtistId(), "SONG", savedSong.getTitle());
        } catch (Exception e) {
            log.error("Failed to send approval notification to artist {}", savedSong.getArtistId(), e);
        }

        // Notificar a los seguidores sobre el nuevo contenido publicado
        try {
            notifyFollowersNewProduct(savedSong);
        } catch (Exception e) {
            log.error("Failed to notify followers about new song {}", songId, e);
        }

        log.info("Canción aprobada y publicada: {} por admin: {}", songId, adminId);
        return savedSong;
    }

    /**
     * GA01-162: Rechazar una canción
     */
    @Transactional
    public Song rejectSong(Long songId, Long adminId, String rejectionReason, String notes) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un motivo de rechazo");
        }

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_SONG_KEY + songId));

        ModerationStatus previousStatus = song.getModerationStatus();

        // Actualizar estado de moderación
        song.setModerationStatus(ModerationStatus.REJECTED);
        song.setModeratedBy(adminId);
        song.setModeratedAt(LocalDateTime.now());
        song.setRejectionReason(rejectionReason);
        song.setPublished(false); // Asegurar que no esté publicada

        Song savedSong = songRepository.save(song);

        // Registrar en historial
        recordModerationHistory(savedSong, previousStatus, ModerationStatus.REJECTED,
                adminId, rejectionReason, notes);

        // Notificar al artista que su canción fue rechazada
        try {
            notificationClient.notifyArtistRejected(savedSong.getArtistId(), "SONG", savedSong.getTitle(), rejectionReason);
        } catch (Exception e) {
            log.error("Failed to send rejection notification to artist {}", savedSong.getArtistId(), e);
        }

        log.info("Canción rechazada: {} por admin: {} - Motivo: {}", songId, adminId, rejectionReason);
        return savedSong;
    }

    /**
     * GA01-162: Aprobar un álbum
     * Al aprobar, el álbum se publica automáticamente
     */
    @Transactional
    public Album approveAlbum(Long albumId, Long adminId, String notes) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_ALBUM_KEY + albumId));

        ModerationStatus previousStatus = album.getModerationStatus();

        // Actualizar estado de moderación
        album.setModerationStatus(ModerationStatus.APPROVED);
        album.setModeratedBy(adminId);
        album.setModeratedAt(LocalDateTime.now());
        album.setRejectionReason(null);
        album.setPublished(true); // Publicar automáticamente al aprobar

        Album savedAlbum = albumRepository.save(album);

        // Registrar en historial
        recordModerationHistory(savedAlbum, previousStatus, ModerationStatus.APPROVED,
                adminId, null, notes);

        // Notificar al artista que su álbum fue aprobado
        try {
            notificationClient.notifyArtistApproved(savedAlbum.getArtistId(), "ALBUM", savedAlbum.getTitle());
        } catch (Exception e) {
            log.error("Failed to send approval notification to artist {}", savedAlbum.getArtistId(), e);
        }

        // Notificar a los seguidores sobre el nuevo contenido publicado
        try {
            notifyFollowersNewProduct(savedAlbum);
        } catch (Exception e) {
            log.error("Failed to notify followers about new album {}", albumId, e);
        }

        log.info("Álbum aprobado y publicado: {} por admin: {}", albumId, adminId);
        return savedAlbum;
    }

    /**
     * GA01-162: Rechazar un álbum
     */
    @Transactional
    public Album rejectAlbum(Long albumId, Long adminId, String rejectionReason, String notes) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un motivo de rechazo");
        }

        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_ALBUM_KEY + albumId));

        ModerationStatus previousStatus = album.getModerationStatus();

        // Actualizar estado de moderación
        album.setModerationStatus(ModerationStatus.REJECTED);
        album.setModeratedBy(adminId);
        album.setModeratedAt(LocalDateTime.now());
        album.setRejectionReason(rejectionReason);
        album.setPublished(false);

        Album savedAlbum = albumRepository.save(album);

        // Registrar en historial
        recordModerationHistory(savedAlbum, previousStatus, ModerationStatus.REJECTED,
                adminId, rejectionReason, notes);

        // Notificar al artista que su álbum fue rechazado
        try {
            notificationClient.notifyArtistRejected(savedAlbum.getArtistId(), "ALBUM", savedAlbum.getTitle(), rejectionReason);
        } catch (Exception e) {
            log.error("Failed to send rejection notification to artist {}", savedAlbum.getArtistId(), e);
        }

        log.info("Álbum rechazado: {} por admin: {} - Motivo: {}", albumId, adminId, rejectionReason);
        return savedAlbum;
    }

    /**
     * GA01-162: Obtener contenido pendiente de moderación
     */
    public List<Song> getPendingSongs() {
        return songRepository.findPendingModerationSongs();
    }

    public List<Album> getPendingAlbums() {
        return albumRepository.findPendingModerationAlbums();
    }

    /**
     * GA01-162: Obtener contenido por estado
     */
    public List<Song> getSongsByStatus(ModerationStatus status) {
        return songRepository.findByModerationStatusOrderByCreatedAtDesc(status);
    }

    public List<Album> getAlbumsByStatus(ModerationStatus status) {
        return albumRepository.findByModerationStatusOrderByCreatedAtDesc(status);
    }

    /**
     * GA01-163: Obtener historial completo de moderaciones
     */
    public List<ModerationHistory> getModerationHistory() {
        return moderationHistoryRepository.findAllByOrderByModeratedAtDesc();
    }

    /**
     * GA01-163: Obtener historial de un producto específico
     */
    public List<ModerationHistory> getProductModerationHistory(Long productId, String productType) {
        return moderationHistoryRepository.findByProductIdAndProductTypeOrderByModeratedAtDesc(
                productId, productType);
    }

    /**
     * GA01-163: Obtener historial de moderaciones de un artista
     */
    public List<ModerationHistory> getArtistModerationHistory(Long artistId) {
        return moderationHistoryRepository.findByArtistIdOrderByModeratedAtDesc(artistId);
    }

    /**
     * GA01-163: Registrar evento de moderación en el historial
     */
    private void recordModerationHistory(Product product, ModerationStatus previousStatus,
                                        ModerationStatus newStatus, Long moderatedBy,
                                        String rejectionReason, String notes) {
        // Obtener información del moderador
        UserDTO moderator = userServiceClient.getUserById(moderatedBy);

        // Obtener información del artista
        UserDTO artist = userServiceClient.getUserById(product.getArtistId());

        // GA01-163: Determinar el nombre del artista con fallbacks apropiados
        String artistName = "Desconocido";
        if (artist != null) {
            // Prioridad: artistName > username > firstName + lastName
            if (artist.getArtistName() != null && !artist.getArtistName().trim().isEmpty()) {
                artistName = artist.getArtistName();
            } else if (artist.getUsername() != null) {
                artistName = artist.getUsername();
            } else if (artist.getFirstName() != null && artist.getLastName() != null) {
                artistName = artist.getFirstName() + " " + artist.getLastName();
            }
        }

        ModerationHistory history = ModerationHistory.builder()
                .productId(product.getId())
                .productType(product.getProductType())
                .productTitle(product.getTitle())
                .artistId(product.getArtistId())
                .artistName(artistName)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .moderatedBy(moderatedBy)
                .moderatorName(moderator != null ? moderator.getUsername() : "Admin #" + moderatedBy)
                .rejectionReason(rejectionReason)
                .notes(notes)
                .moderatedAt(LocalDateTime.now())
                .build();

        moderationHistoryRepository.save(history);
        log.debug("Historial de moderación registrado para producto {} ({})", product.getId(), product.getProductType());
    }

    /**
     * Marcar contenido como pendiente de revisión (cuando se modifica)
     */
    @Transactional
    public void markSongAsPending(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_SONG_KEY + songId));

        ModerationStatus previousStatus = song.getModerationStatus();
        if (previousStatus != ModerationStatus.PENDING) {
            song.setModerationStatus(ModerationStatus.PENDING);
            song.setModeratedBy(null);
            song.setModeratedAt(null);
            song.setRejectionReason(null);
            song.setPublished(false);
            songRepository.save(song);

            log.info("Canción {} marcada como pendiente de revisión", songId);
        }
    }

    @Transactional
    public void markAlbumAsPending(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_ALBUM_KEY + albumId));

        ModerationStatus previousStatus = album.getModerationStatus();
        if (previousStatus != ModerationStatus.PENDING) {
            album.setModerationStatus(ModerationStatus.PENDING);
            album.setModeratedBy(null);
            album.setModeratedAt(null);
            album.setRejectionReason(null);
            album.setPublished(false);
            albumRepository.save(album);

            log.info("Álbum {} marcado como pendiente de revisión", albumId);
        }
    }

    /**
     * Notificar a los seguidores de un artista sobre un nuevo producto publicado
     */
    private void notifyFollowersNewProduct(Product product) {
        try {
            // Obtener seguidores del artista
            List<Long> followerIds = userServiceClient.getFollowerIds(product.getArtistId());

            if (followerIds.isEmpty()) {
                log.debug("Artista {} no tiene seguidores para notificar", product.getArtistId());
                return;
            }

            // Obtener información del artista
            UserDTO artist = userServiceClient.getUserById(product.getArtistId());
            String artistName = null;

            if (artist != null) {
                if (artist.getArtistName() != null) {
                    artistName = artist.getArtistName();
                } else {
                    artistName = artist.getUsername();
                }
            }

            if (artistName == null) {
                log.error("Cannot notify followers: artist name is null for artistId {}", product.getArtistId());
                return; // Don't send notifications with null artist name
            }

            // Enviar notificación a cada seguidor
            for (Long followerId : followerIds) {
                notifySingleFollower(followerId, product, artistName);
            }

            log.info("Notificados {} seguidores sobre nuevo producto: {} ({})",
                followerIds.size(), product.getTitle(), product.getProductType());

        } catch (Exception e) {
            log.error("Error notifying followers about new product {}", product.getId(), e);
        }
    }

    private void notifySingleFollower(Long followerId, Product product, String artistName) {
        try {
            notificationClient.notifyNewProduct(
                followerId,
                product.getProductType(),
                product.getTitle(),
                artistName
            );
        } catch (Exception e) {
            // Capturamos la excepción aquí para que el bucle principal continúe
            log.warn("Failed to notify follower {} about new product {}", followerId, product.getId(), e);
        }
    }
}
