package io.audira.catalog.controller;

import io.audira.catalog.dto.RecommendationsResponse;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.Song;
import io.audira.catalog.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para las funcionalidades de exploración y descubrimiento de música.
 * <p>
 * Incluye tendencias, búsqueda avanzada y motores de recomendación personalizados.
 * </p>
 */
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    /**
     * Obtiene una lista de canciones en tendencia.
     *
     * @param limit Límite de resultados a mostrar.
     * @return Lista de canciones populares actualmente.
     */
    @GetMapping("/trending/songs")
    public ResponseEntity<List<Song>> getTrendingSongs(@RequestParam(defaultValue = "20") int limit) {
        List<Song> songs = discoveryService.getTrendingSongs();
        return ResponseEntity.ok(songs.stream().limit(limit).toList());
    }

    /**
     * Motor de búsqueda avanzado para canciones.
     * <p>
     * Permite filtrar por texto, género y rango de precios, además de ordenar los resultados.
     * Implementa paginación.
     * </p>
     *
     * @param query Texto a buscar (título, artista).
     * @param genreId (Opcional) Filtro por género.
     * @param minPrice (Opcional) Precio mínimo.
     * @param maxPrice (Opcional) Precio máximo.
     * @param sortBy Criterio de ordenación ("recent", "price_asc", "price_desc").
     * @param page Número de página.
     * @param size Tamaño de página.
     * @return Mapa con la lista de canciones y metadatos de paginación.
     */
    @GetMapping("/search/songs")
    public ResponseEntity<Map<String, Object>> searchSongs(
            @RequestParam("query") String query,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "recent") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Song> songPage = discoveryService.searchSongs(query, genreId, minPrice, maxPrice, sortBy, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", songPage.getContent());
        response.put("currentPage", songPage.getNumber());
        response.put("totalItems", songPage.getTotalElements());
        response.put("totalPages", songPage.getTotalPages());
        response.put("hasMore", songPage.hasNext());

        return ResponseEntity.ok(response);
    }

    /**
     * Motor de búsqueda avanzado para álbumes.
     * <p>
     * Permite filtrar por texto, género y rango de precios, además de ordenar los resultados.
     * Implementa paginación.
     * </p>
     *
     * @param query Texto a buscar (título, artista).
     * @param genreId (Opcional) Filtro por género.
     * @param minPrice (Opcional) Precio mínimo.
     * @param maxPrice (Opcional) Precio máximo.
     * @param sortBy Criterio de ordenación ("recent", "price_asc", "price_desc").
     * @param page Número de página.
     * @param size Tamaño de página.
     * @return Mapa con la lista de álbumes y metadatos de paginación.
     */
    @GetMapping("/search/albums")
    public ResponseEntity<Map<String, Object>> searchAlbums(
            @RequestParam("query") String query,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "recent") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Album> albumPage = discoveryService.searchAlbums(query, genreId, minPrice, maxPrice, sortBy, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", albumPage.getContent());
        response.put("currentPage", albumPage.getNumber());
        response.put("totalItems", albumPage.getTotalElements());
        response.put("totalPages", albumPage.getTotalPages());
        response.put("hasMore", albumPage.hasNext());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene recomendaciones personalizadas para un usuario.
     * <p>
     * GA01-117: Módulo básico de recomendaciones.
     * </p>
     *
     * @param userId ID del usuario (query param).
     * @return Objeto con recomendaciones categorizadas.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestParam Long userId) {         
        RecommendationsResponse recommendations = discoveryService.getRecommendationsForUser(userId);
        return ResponseEntity.ok(recommendations);
    }
}