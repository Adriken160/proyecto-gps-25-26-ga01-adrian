package io.audira.catalog.controller;

import io.audira.catalog.model.Playlist;
import io.audira.catalog.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para la gestión de listas de reproducción (Playlists).
 * <p>
 * Implementa GA01-113, GA01-114 y GA01-115 (CRUD y gestión de canciones).
 * </p>
 */
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private static final String ERROR_KEY = "error";
    private final PlaylistService playlistService;

    /**
     * Obtiene todas las listas de reproducción públicas.
     * @return Lista de playlists.
     */
    @GetMapping
    public ResponseEntity<List<Playlist>> getAllPlaylists() {
        return ResponseEntity.ok(playlistService.getAllPlaylists());
    }

    /**
     * Obtiene el detalle de una playlist por ID.
     * @param id ID de la playlist.
     * @return Detalle de la playlist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getPlaylistById(@PathVariable Long id) {
        Optional<Playlist> playlist = playlistService.getPlaylistById(id);
        if (playlist.isPresent()) {
            return ResponseEntity.ok(playlist.get());
        } else {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, "Playlist not found with id: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Obtiene todas las listas de reproducción de un usuario específico.
     * @param userId ID del usuario.
     * @return Lista de playlists del usuario.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Playlist>> getUserPlaylists(@PathVariable Long userId) {
        return ResponseEntity.ok(playlistService.getUserPlaylists(userId));
    }

    /**
     * Obtiene todas las listas de reproducción públicas.
     * @return Lista de playlists públicas.
     */
    @GetMapping("/public")
    public ResponseEntity<List<Playlist>> getPublicPlaylists() {
        return ResponseEntity.ok(playlistService.getPublicPlaylists());
    }

    /**
     * Crea una nueva lista de reproducción.
     * @param playlist Objeto playlist con nombre y descripción.
     * @return Playlist creada.
     */
    @PostMapping
    public ResponseEntity<Object> createPlaylist(@RequestBody Playlist playlist) {
        try {
            Playlist createdPlaylist = playlistService.createPlaylist(playlist);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPlaylist);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Actualiza el nombre o descripción de una playlist.
     * @param id ID de la playlist.
     * @param playlistDetails Nuevos datos.
     * @return Playlist actualizada.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Object> updatePlaylist(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        try {
            String name = (String) updates.get("name");
            String description = (String) updates.get("description");
            Boolean isPublic = (Boolean) updates.get("isPublic");

            Playlist updatedPlaylist = playlistService.updatePlaylist(id, name, description, isPublic);
            return ResponseEntity.ok(updatedPlaylist);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Elimina una playlist completa.
     * @param id ID de la playlist.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deletePlaylist(@PathVariable Long id) {
        try {
            playlistService.deletePlaylist(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Añade una canción a una playlist.
     * @param id ID de la playlist.
     * @param songId ID de la canción a añadir.
     * @return Playlist actualizada.
     */
    @PostMapping("/{id}/songs")
    public ResponseEntity<Object> addSongToPlaylist(
            @PathVariable Long id,
            @RequestParam Long songId) {
        try {
            Playlist updatedPlaylist = playlistService.addSongToPlaylist(id, songId);
            return ResponseEntity.ok(updatedPlaylist);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * Elimina una canción de una playlist.
     * @param id ID de la playlist.
     * @param songId ID de la canción a eliminar.
     * @return Playlist actualizada.
     */
    @DeleteMapping("/{id}/songs/{songId}")
    public ResponseEntity<Object> removeSongFromPlaylist(
            @PathVariable Long id,
            @PathVariable Long songId) {
        try {
            Playlist updatedPlaylist = playlistService.removeSongFromPlaylist(id, songId);
            return ResponseEntity.ok(updatedPlaylist);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Reordena las canciones dentro de una playlist.
     * @param id ID de la playlist.
     * @param request Mapa con la lista "songIds" en el nuevo orden.
     * @return Playlist actualizada.
     */
    @PutMapping("/{id}/songs/reorder")
    public ResponseEntity<Object> reorderPlaylistSongs(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> songIds = request.get("songIds");
            if (songIds == null) {
                Map<String, String> error = new HashMap<>();
                error.put(ERROR_KEY, "songIds is required");
                return ResponseEntity.badRequest().body(error);
            }

            Playlist updatedPlaylist = playlistService.reorderPlaylistSongs(id, songIds);
            return ResponseEntity.ok(updatedPlaylist);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Busca en qué playlists del usuario aparece una canción específica.
     * @param userId ID del usuario.
     * @param songId ID de la canción.
     * @return Lista de playlists que contienen la canción.
     */
    @GetMapping("/user/{userId}/song/{songId}")
    public ResponseEntity<List<Playlist>> getPlaylistsContainingSong(
            @PathVariable Long userId,
            @PathVariable Long songId) {
        return ResponseEntity.ok(playlistService.getPlaylistsContainingSong(userId, songId));
    }
}
