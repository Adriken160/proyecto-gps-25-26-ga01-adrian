package io.audira.catalog.controller;

import io.audira.catalog.dto.SongDTO;
import io.audira.catalog.model.Song;
import io.audira.catalog.service.SongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de canciones (Tracks).
 * <p>
 * Maneja operaciones CRUD, control de reproducciones (plays) y búsquedas públicas.
 * </p>
 */
@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
@Slf4j
public class SongController {

    private static final String ERROR_KEY = "error";
    private final SongService songService;

    /**
     * Registra una nueva canción en el sistema.
     *
     * @param song Objeto {@link Song} con los metadatos de la canción.
     * @return La canción creada o un error 400 si los datos son inválidos.
     */
    @PostMapping
    public ResponseEntity<Object> createSong(@RequestBody Song song) {
        try {
            Song createdSong = songService.createSong(song);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSong);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Obtiene todas las canciones registradas, incluyendo el nombre del artista enriquecido.
     *
     * @return Lista de DTOs de canciones.
     */
    @GetMapping
    public ResponseEntity<List<SongDTO>> getAllSongs() {
        return ResponseEntity.ok(songService.getAllSongsWithArtistName());
    }
 
    /**
     * Obtiene el detalle de una canción específica.
     *
     * @param id ID de la canción.
     * @return Detalles de la canción o 404 si no existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getSongById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(songService.getSongByIdWithArtistName(id));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
 
    /**
     * Obtiene las canciones asociadas a un artista específico.
     *
     * @param artistId ID del artista.
     * @return Lista de canciones del artista.
     */
    @GetMapping("/artist/{artistId}")
    public ResponseEntity<List<SongDTO>> getSongsByArtist(@PathVariable Long artistId) {
        return ResponseEntity.ok(songService.getSongsByArtistWithArtistName(artistId));
    }
 
    /**
     * Obtiene las canciones asociadas a un álbum específico.
     *
     * @param albumId ID del álbum.
     * @return Lista de canciones del álbum.
     */
    @GetMapping("/album/{albumId}")
    public ResponseEntity<List<SongDTO>> getSongsByAlbum(@PathVariable Long albumId) {
        return ResponseEntity.ok(songService.getSongsByAlbumWithArtistName(albumId));
    }
 
    /**
     * Obtiene las canciones de un género musical específico.
     *
     * @param genreId ID del género.
     * @return Lista de canciones del género.
     */
    @GetMapping("/genre/{genreId}")
    public ResponseEntity<List<SongDTO>> getSongsByGenre(@PathVariable Long genreId) {
        return ResponseEntity.ok(songService.getSongsByGenreWithArtistName(genreId));
    }
 
    /**
     * Obtiene las canciones más recientes.
     *
     * @return Lista de canciones recientes.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<SongDTO>> getRecentSongs() {
        return ResponseEntity.ok(songService.getRecentSongsWithArtistName());
    }
 
    /**
     * Obtiene el ranking de las canciones más escuchadas (Top Charts).
     *
     * @return Lista de canciones ordenadas por número de reproducciones.
     */
    @GetMapping("/top")
    public ResponseEntity<List<SongDTO>> getTopSongs() {
        return ResponseEntity.ok(songService.getTopSongsByPlaysWithArtistName());
    }
 
    /**
     * Busca canciones por título.
     *
     * @param query Término de búsqueda.
     * @return Lista de coincidencias.
     */
    @GetMapping("/search")
    public ResponseEntity<List<SongDTO>> searchSongs(@RequestParam String query) {
        return ResponseEntity.ok(songService.searchSongsWithArtistName(query));
    }

    /**
     * Actualiza los metadatos de una canción existente.
     *
     * @param id ID de la canción.
     * @param songDetails Datos a actualizar.
     * @return La canción actualizada.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateSong(@PathVariable Long id, @RequestBody Song song) {
        try {
            return ResponseEntity.ok(songService.updateSong(id, song));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Elimina una canción del catálogo.
     *
     * @param id ID de la canción.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSong(@PathVariable Long id) {
        try {
            songService.deleteSong(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Incrementa el contador de reproducciones de una canción.
     * <p>
     * Debe ser invocado por el cliente o el servicio de streaming cada vez que
     * un usuario escucha la canción.
     * </p>
     *
     * @param id ID de la canción.
     * @return La canción con el contador actualizado.
     */
    @PostMapping("/{id}/play")
    public ResponseEntity<Song> incrementPlays(@PathVariable Long id) {
        try {
            log.info("📊 Incrementing play count for song ID: {}", id);
            Song updatedSong = songService.incrementPlays(id);
            log.info("✅ Play count incremented successfully - Song: '{}', New count: {}",
                    updatedSong.getTitle(), updatedSong.getPlays());
            return ResponseEntity.ok(updatedSong);
        } catch (IllegalArgumentException e) {
            log.warn("❌ Failed to increment play count for song ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cambia el estado de publicación de una canción.
     *
     * @param id ID de la canción.
     * @param published Nuevo estado de publicación.
     * @return La canción actualizada.
     */
    @PatchMapping("/{id}/publish")
    public ResponseEntity<Song> publishSong(@PathVariable Long id, @RequestParam boolean published) {
        try {
            return ResponseEntity.ok(songService.publishSong(id, published));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Obtiene las canciones publicadas más recientes.
     * @return Lista de canciones recientes.
     */
    @GetMapping("/public/recent")
    public ResponseEntity<List<SongDTO>> getRecentPublishedSongs() {
        return ResponseEntity.ok(songService.getRecentPublishedSongsWithArtistName());
    }
 
    /**
     * Obtiene el ranking de las canciones más escuchadas (Top Charts).
     * @return Lista de canciones ordenadas por número de reproducciones.
     */
    @GetMapping("/public/top")
    public ResponseEntity<List<SongDTO>> getTopPublishedSongs() {
        return ResponseEntity.ok(songService.getTopPublishedSongsByPlaysWithArtistName());
    }
 
    /**
     * Busca canciones publicadas por título.
     * @param query Término de búsqueda.
     * @return Lista de coincidencias.
     */
    @GetMapping("/public/search")
    public ResponseEntity<List<SongDTO>> searchPublishedSongs(@RequestParam String query) {
        return ResponseEntity.ok(songService.searchPublishedSongsWithArtistName(query));
    }
 
    /**
     * Filtra canciones publicadas por género musical.
     * @param genreId ID del género.
     * @return Lista de canciones del género especificado.
     */
    @GetMapping("/public/genre/{genreId}")
    public ResponseEntity<List<SongDTO>> getPublishedSongsByGenre(@PathVariable Long genreId) {
        return ResponseEntity.ok(songService.getPublishedSongsByGenreWithArtistName(genreId));
    }

    /**
     * Endpoint transaccional para el microservicio de Comercio.
     * <p>
     * Recupera el ID del artista y el precio de una canción.
     * </p>
     *
     * @param id ID de la canción.
     * @return Map con artistId y price o 404 si no existe.
     */
    @GetMapping("/{id}/details/commerce") // Endpoint claro para uso interno
    public ResponseEntity<Object> getSongDetailsForCommerce(@PathVariable Long id) {
        try {
            Map<String, Object> details = songService.getArtistAndPriceBySongId(id);
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException e) {
            log.warn("❌ Failed to fetch commerce details for song ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
