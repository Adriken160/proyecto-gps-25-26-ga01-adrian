package io.audira.catalog.service;

import io.audira.catalog.client.CommerceServiceClient;
import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.OrderDTO;
import io.audira.catalog.dto.OrderItemDTO;
import io.audira.catalog.dto.RecommendationsResponse;
import io.audira.catalog.dto.RecommendedSong;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.Song;
import io.audira.catalog.repository.AlbumRepository;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la lógica de descubrimiento y recomendación.
 * <p>
 * Agrega información de múltiples dominios (Catálogo, Comercio, Usuarios) para generar
 * listas de reproducción sugeridas, tendencias y resultados de búsqueda enriquecidos.
 * Implementa el requisito <b>GA01-117: Módulo básico de recomendaciones</b>.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryService {
    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final CommerceServiceClient commerceServiceClient;
    private final UserServiceClient userServiceClient;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${community.service.url:http://172.16.0.4:9001}")
    private String communityServiceUrl;

    private static final int RECOMMENDATIONS_PER_CATEGORY = 10;
    private static final String CREATE_KEY = "createdAt";
    private static final String PRICE_KEY = "price";
    private static final String DELIVERED_KEY = "DELIVERED";

    /**
     * Obtiene una lista de canciones que son tendencia actualmente.
     * <p>
     * Se basa en el número de reproducciones o popularidad reciente.
     * </p>
     *
     * @param limit El número máximo de canciones a recuperar (por defecto 20).
     * @return Una lista de objetos {@link Song} que representan las canciones en tendencia.
     */
    public List<Song> getTrendingSongs() {
        return songRepository.findTopByPlays();
    }

    /**
     * Obtiene una lista de álbumes que son tendencia actualmente.
     * <p>
     * Se basa en la fecha de creación o popularidad reciente.
     * </p>
     *
     * @return Una lista de objetos {@link Album} que representan los álbumes en tendencia.
     */
    public List<Album> getTrendingAlbums() {
        return albumRepository.findTop20ByOrderByCreatedAtDesc();
    }

    /**
     * Búsqueda simplificada de canciones por texto.
     * <p>
     * Sobrecarga del método de búsqueda principal que utiliza valores por defecto para los filtros.
     * Útil para la barra de búsqueda rápida del encabezado.
     * </p>
     *
     * @param query Texto de búsqueda (título o artista).
     * @param pageable Configuración de paginación.
     * @return Página de canciones coincidentes.
     * @see #searchSongs(String, Long, Double, Double, String, Pageable)
     */
    public Page<Song> searchSongs(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> artistIds = getArtistIdsByName(query);

        if (artistIds.isEmpty()) {
            return songRepository.searchByTitle(query, pageable);
        } else {
            return songRepository.searchByTitleOrArtistIds(query, artistIds, pageable);
        }
    }

    /**
     * Realiza una búsqueda avanzada de canciones aplicando múltiples filtros.
     * <p>
     * Permite buscar por texto libre (título o artista) y refinar por género y rango de precios.
     * Soporta paginación y ordenamiento dinámico.
     * </p>
     *
     * @param query    Texto de búsqueda para coincidencia en título o nombre del artista.
     * @param genreId  (Opcional) ID del género para filtrar.
     * @param minPrice (Opcional) Precio mínimo.
     * @param maxPrice (Opcional) Precio máximo.
     * @param sortBy   Criterio de ordenación ("recent", "price_asc", "price_desc", "popularity").
     * @param pageable Configuración de paginación (página y tamaño).
     * @return Una página {@link Page} de canciones que cumplen con los criterios.
     */
    public Page<Song> searchSongs(String query, Long genreId, Double minPrice, Double maxPrice, String sortBy, Pageable pageable) {
        log.debug("Advanced search - query: {}, genreId: {}, minPrice: {}, maxPrice: {}, sortBy: {}",
                query, genreId, minPrice, maxPrice, sortBy);

        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasFilters = genreId != null || minPrice != null || maxPrice != null;

        Sort sort = Sort.by(Sort.Direction.DESC, CREATE_KEY); // Default: newest first
        if ("price_asc".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, PRICE_KEY);
        } else if ("price_desc".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, PRICE_KEY);
        } else if ("oldest".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, CREATE_KEY);
        } else if ("recent".equals(sortBy) || "newest".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, CREATE_KEY);
        }

        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        if (!hasQuery && hasFilters) {
            log.debug("Searching with filters only");
            return songRepository.searchPublishedByFiltersOnly(genreId, minPrice, maxPrice, sortedPageable);
        }

        if (hasQuery) {
            String searchQuery = query.trim();
            List<Long> artistIds = getArtistIdsByName(searchQuery);

            if (artistIds.isEmpty()) {
                log.debug("Searching by title with filters: {}", searchQuery);
                return songRepository.searchPublishedByTitleAndFilters(searchQuery, genreId, minPrice, maxPrice, sortedPageable);
            }

            log.debug("Searching by title or artist IDs with filters: {}, artistIds: {}", searchQuery, artistIds);
            return songRepository.searchPublishedByTitleOrArtistIdsAndFilters(searchQuery, artistIds, genreId, minPrice, maxPrice, sortedPageable);
        }

        log.debug("No query and no filters provided, returning empty page");
        return Page.empty(sortedPageable);
    }   

    /**
     * Busca álbumes que coincidan con el criterio de texto.
     * <p>
     * Permite a los usuarios encontrar álbumes por título o por nombre del artista.
     * Delega en el {@link AlbumRepository} la ejecución de la consulta.
     * </p>
     *
     * @param query Texto de búsqueda.
     * @param pageable Configuración de paginación.
     * @return Página de álbumes coincidentes.
     */
    public Page<Album> searchAlbums(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> artistIds = getArtistIdsByName(query);

        if (artistIds.isEmpty()) {
            return albumRepository.searchByTitle(query, pageable);
        } else {
            return albumRepository.searchByTitleOrArtistIds(query, artistIds, pageable);
        }
    }

    /**
     * Realiza una búsqueda avanzada de álbumes aplicando múltiples filtros.
     * <p>
     * Permite buscar por texto libre (título o artista) y refinar por género y rango de precios.
     * Soporta paginación y ordenamiento dinámico.
     * </p>
     *
     * @param query    Texto de búsqueda para coincidencia en título o nombre del artista.
     * @param genreId  (Opcional) ID del género para filtrar.
     * @param minPrice (Opcional) Precio mínimo.
     * @param maxPrice (Opcional) Precio máximo.
     * @param sortBy   Criterio de ordenación ("recent", "price_asc", "price_desc", "popularity").
     * @param pageable Configuración de paginación (página y tamaño).
     * @return Una página {@link Page} de álbumes que cumplen con los criterios.
     */
    public Page<Album> searchAlbums(String query, Long genreId, Double minPrice, Double maxPrice, String sortBy, Pageable pageable) {
        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasFilters = genreId != null || minPrice != null || maxPrice != null;
        
        if (!hasQuery && !hasFilters) {
            return Page.empty(pageable);
        }
        
        String searchQuery = hasQuery ? query : "";

        Sort sort = Sort.by(Sort.Direction.DESC, "releaseDate");
        if ("price_asc".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, PRICE_KEY);
        } else if ("price_desc".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, PRICE_KEY);
        } else if ("oldest".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, CREATE_KEY);
        }

        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        log.info("DEBUG SEARCH ALBUMS -> Query: {}, GenreID: {}, MinPrice: {}, MaxPrice: {}, SortBy: {}", 
                 searchQuery, genreId, minPrice, maxPrice, sortBy);
        
        if (searchQuery.isEmpty()) {
            return albumRepository.searchPublishedByFiltersOnly(genreId, minPrice, maxPrice, sortedPageable);
        }
        
        List<Long> artistIds = getArtistIdsByName(searchQuery);
        log.info("DEBUG SEARCH ALBUMS -> ArtistIds encontrados: {}", artistIds);

        if (artistIds.isEmpty()) {
            return albumRepository.searchPublishedByTitleAndFilters(searchQuery, genreId, minPrice, maxPrice, sortedPageable);
        } else {
            return albumRepository.searchPublishedByTitleOrArtistIdsAndFilters(searchQuery, artistIds, genreId, minPrice, maxPrice, sortedPageable);
        }
    }

    /**
     * Método auxiliar para resolver IDs de artistas a partir de un nombre.
     * <p>
     * Consulta al {@code UserServiceClient} para buscar usuarios con rol 'ARTIST'
     * cuyo nombre coincida parcialmente con la query.
     * </p>
     *
     * @param name Nombre o fragmento del nombre del artista.
     * @return Lista de IDs de artistas encontrados.
     */
    private List<Long> getArtistIdsByName(String query) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(communityServiceUrl + "/api/users/search/artist-ids")
                    .queryParam("query", query)
                    .toUriString();

            ResponseEntity<List<Long>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Long>>() {}
            );

            return response.getBody() != null ? response.getBody() : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to get artist IDs from community-service: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Genera un conjunto de recomendaciones personalizadas para un usuario específico.
     * <p>
     * Orquesta la obtención de datos de contexto (como el historial de pedidos) y aplica
     * diversas estrategias de recomendación (tendencias, historial, artistas seguidos) para
     * construir una respuesta completa y categorizada.
     * </p>
     *
     * @param userId El ID del usuario para el cual se generan las recomendaciones.
     * @return Un objeto {@link RecommendationsResponse} que contiene listas de canciones sugeridas agrupadas por categoría.
     */
    public RecommendationsResponse getRecommendationsForUser(Long userId) {
        log.info("Generating recommendations for user {}", userId);

        RecommendationsResponse response = RecommendationsResponse.builder()
                .userId(userId)
                .generatedAt(LocalDateTime.now())
                .algorithm("basic_placeholder_v1")
                .build();

        try {
            response.setByPurchasedGenres(getRecommendationsByPurchasedGenres(userId));

            response.setByPurchasedArtists(getRecommendationsByPurchasedArtists(userId));

            response.setByLikedSongs(getRecommendationsByLikedSongs(userId));

            response.setFromFollowedArtists(getRecommendationsFromFollowedArtists(userId));

            response.setBasedOnPurchases(getRecommendationsFromPurchaseHistory(userId));

            response.setTrending(getTrendingRecommendations());

            response.setNewReleases(getNewReleasesRecommendations());

            response.setSimilarToFavorites(getRecommendationsFromPurchaseHistory(userId));

            response.setBasedOnListeningHistory(new ArrayList<>());

            int total = safeListSize(response.getByPurchasedGenres())
                    + safeListSize(response.getByPurchasedArtists())
                    + safeListSize(response.getByLikedSongs())
                    + safeListSize(response.getBasedOnPurchases())
                    + safeListSize(response.getFromFollowedArtists())
                    + safeListSize(response.getTrending())
                    + safeListSize(response.getNewReleases())
                    + safeListSize(response.getSimilarToFavorites())
                    + safeListSize(response.getBasedOnListeningHistory());

            response.setTotalRecommendations(total);

            log.info("Generated {} total recommendations for user {} - By genres: {}, By artists: {}, By likes: {}, From followed: {}, Trending: {}, New: {}",
                    total, userId,
                    safeListSize(response.getByPurchasedGenres()),
                    safeListSize(response.getByPurchasedArtists()),
                    safeListSize(response.getByLikedSongs()),
                    safeListSize(response.getFromFollowedArtists()),
                    safeListSize(response.getTrending()),
                    safeListSize(response.getNewReleases()));

        } catch (Exception e) {
            log.error("Error generating recommendations for user {}", userId, e);
            response.setByPurchasedGenres(new ArrayList<>());
            response.setByPurchasedArtists(new ArrayList<>());
            response.setByLikedSongs(new ArrayList<>());
            response.setBasedOnPurchases(new ArrayList<>());
            response.setFromFollowedArtists(new ArrayList<>());
            response.setTrending(new ArrayList<>());
            response.setNewReleases(new ArrayList<>());
            response.setSimilarToFavorites(new ArrayList<>());
            response.setBasedOnListeningHistory(new ArrayList<>());
            response.setTotalRecommendations(0);
        }

        return response;
    }

    /**
     * Genera recomendaciones basadas en el historial de compras general.
     * <p>
     * Analiza patrones de compra para sugerir contenido complementario.
     * A diferencia de los métodos específicos de género/artista, este método busca
     * correlaciones más amplias (ej: "Usuarios que compraron lo que tú compraste, también compraron...").
     * </p>
     *
     * @param userId ID del usuario.
     * @return Lista de canciones recomendadas.
     */
    private List<RecommendedSong> getRecommendationsFromPurchaseHistory(Long userId) {
        try {
            List<OrderDTO> orders = commerceServiceClient.getUserOrders(userId);

            Set<Long> purchasedSongIds = orders.stream()
                    .filter(order -> order.getStatus() != null && DELIVERED_KEY.equals(order.getStatus()))
                    .flatMap(order -> order.getItems().stream())
                    .filter(item -> "SONG".equals(item.getItemType()))
                    .map(OrderItemDTO::getItemId)
                    .collect(Collectors.toSet());

            if (purchasedSongIds.isEmpty()) {
                log.debug("User {} has no delivered purchase history", userId);
                return new ArrayList<>();
            }

            List<Song> purchasedSongs = songRepository.findAllById(purchasedSongIds);

            Set<Long> favoriteGenres = purchasedSongs.stream()
                    .flatMap(song -> song.getGenreIds().stream())
                    .collect(Collectors.toSet());

            if (favoriteGenres.isEmpty()) {
                return new ArrayList<>();
            }

            List<Song> recommendations = new ArrayList<>();
            for (Long genreId : favoriteGenres) {
                List<Song> genreSongs = songRepository.findPublishedByGenreId(genreId);
                recommendations.addAll(genreSongs.stream()
                        .filter(song -> !purchasedSongIds.contains(song.getId()))
                        .limit(5)
                        .toList());
            }

            List<Song> limitedRecommendations = recommendations.stream()
                    .distinct()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(limitedRecommendations, "Based on your purchase history", 0.85);

        } catch (Exception e) {
            log.warn("Error getting recommendations from purchase history for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Obtiene los últimos lanzamientos de los artistas que el usuario sigue.
     * <p>
     * Consulta el grafo social (Seguidores) y filtra el contenido publicado recientemente
     * por esos artistas. Prioridad alta en el feed del usuario.
     * </p>
     *
     * @param userId ID del usuario seguidor.
     * @return Lista de nuevas canciones de sus artistas favoritos.
     */
    private List<RecommendedSong> getRecommendationsFromFollowedArtists(Long userId) {
        try {
            List<Long> followedArtistIds = userServiceClient.getFollowedArtistIds(userId);

            if (followedArtistIds.isEmpty()) {
                log.debug("User {} doesn't follow any artists", userId);
                return new ArrayList<>();
            }

            List<Song> artistSongs = new ArrayList<>();
            for (Long artistId : followedArtistIds) {
                List<Song> songs = songRepository.findByArtistId(artistId).stream()
                        .filter(Song::isPublished)
                        .limit(3) // Limit per artist to ensure variety
                        .toList();
                artistSongs.addAll(songs);
            }

            List<Song> limitedSongs = artistSongs.stream()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(limitedSongs, "From artists you follow", 0.9);

        } catch (Exception e) {
            log.warn("Error getting recommendations from followed artists for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Obtiene las recomendaciones de tendencia general.
     * <p>
     * Wrapper sobre {@link #getTrendingSongs(int)} que devuelve el objeto enriquecido
     * {@link RecommendedSong} listo para la UI.
     * </p>
     *
     * @return Lista de canciones en tendencia con metadatos.
     */
    private List<RecommendedSong> getTrendingRecommendations() {
        try {
            List<Song> trendingSongs = songRepository.findTopPublishedByPlays().stream()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(trendingSongs, "Trending now", 0.7);

        } catch (Exception e) {
            log.warn("Error getting trending recommendations", e);
            return new ArrayList<>();
        }
    }

    /**
     * Obtiene recomendaciones de nuevos lanzamientos globales.
     * <p>
     * Filtra las canciones publicadas en la última semana, ordenadas por fecha.
     * </p>
     *
     * @return Lista de novedades ("New Releases").
     */
    private List<RecommendedSong> getNewReleasesRecommendations() {
        try {
            List<Song> newSongs = songRepository.findTop20ByPublishedTrueOrderByCreatedAtDesc().stream()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(newSongs, "New release", 0.75);

        } catch (Exception e) {
            log.warn("Error getting new releases recommendations", e);
            return new ArrayList<>();
        }
    }

    /**
     * Genera recomendaciones basadas en los géneros de música que el usuario ha comprado previamente.
     * <p>
     * Analiza el historial de pedidos para identificar los géneros más frecuentes y busca
     * canciones populares de esos mismos géneros que el usuario no haya comprado aún.
     * </p>
     *
     * @param userId     ID del usuario.
     * @param userOrders Historial de pedidos del usuario (para evitar rellamadas).
     * @return Lista de canciones recomendadas por afinidad de género.
     */
    private List<RecommendedSong> getRecommendationsByPurchasedGenres(Long userId) {
        try {
            List<OrderDTO> orders = commerceServiceClient.getUserOrders(userId);

            Set<Long> purchasedSongIds = orders.stream()
                    .filter(order -> order.getStatus() != null && DELIVERED_KEY.equals(order.getStatus()))
                    .flatMap(order -> order.getItems().stream())
                    .filter(item -> "SONG".equals(item.getItemType()))
                    .map(OrderItemDTO::getItemId)
                    .collect(Collectors.toSet());

            if (purchasedSongIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<Song> purchasedSongs = songRepository.findAllById(purchasedSongIds);
            Set<Long> favoriteGenres = purchasedSongs.stream()
                    .flatMap(song -> song.getGenreIds().stream())
                    .collect(Collectors.toSet());

            if (favoriteGenres.isEmpty()) {
                return new ArrayList<>();
            }

            List<Song> recommendations = new ArrayList<>();
            for (Long genreId : favoriteGenres) {
                List<Song> genreSongs = songRepository.findPublishedByGenreId(genreId);
                recommendations.addAll(genreSongs.stream()
                        .filter(song -> !purchasedSongIds.contains(song.getId()))
                        .limit(4)
                        .toList());
            }

            List<Song> limited = recommendations.stream()
                    .distinct()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(limited, "Songs from genres you love", 0.88);

        } catch (Exception e) {
            log.warn("Error getting recommendations by purchased genres for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Genera recomendaciones basadas en otros trabajos de los artistas que el usuario ha comprado.
     * <p>
     * Fomenta el descubrimiento de catálogo profundo (Deep Catalog) de artistas conocidos por el usuario.
     * </p>
     *
     * @param userId     ID del usuario.
     * @param userOrders Historial de pedidos del usuario.
     * @return Lista de canciones recomendadas por afinidad de artista.
     */
    private List<RecommendedSong> getRecommendationsByPurchasedArtists(Long userId) {
        try {
            List<OrderDTO> orders = commerceServiceClient.getUserOrders(userId);

            Set<Long> purchasedSongIds = orders.stream()
                    .filter(order -> order.getStatus() != null && DELIVERED_KEY.equals(order.getStatus()))
                    .flatMap(order -> order.getItems().stream())
                    .filter(item -> "SONG".equals(item.getItemType()))
                    .map(OrderItemDTO::getItemId)
                    .collect(Collectors.toSet());

            if (purchasedSongIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<Song> purchasedSongs = songRepository.findAllById(purchasedSongIds);
            Set<Long> purchasedArtistIds = purchasedSongs.stream()
                    .map(Song::getArtistId)
                    .collect(Collectors.toSet());

            List<Song> recommendations = new ArrayList<>();
            for (Long artistId : purchasedArtistIds) {
                List<Song> artistSongs = songRepository.findByArtistId(artistId).stream()
                        .filter(Song::isPublished)
                        .filter(song -> !purchasedSongIds.contains(song.getId()))
                        .limit(3)
                        .toList();
                recommendations.addAll(artistSongs);
            }

            List<Song> limited = recommendations.stream()
                    .distinct()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(limited, "More from artists you bought", 0.92);

        } catch (Exception e) {
            log.warn("Error getting recommendations by purchased artists for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Genera recomendaciones basadas en canciones marcadas con "Me gusta".
     * <p>
     * Utiliza las canciones con alta valoración (4-5 estrellas) del usuario como semilla
     * para encontrar contenido similar en términos de género o características de audio.
     * </p>
     *
     * @param userId ID del usuario.
     * @return Lista de canciones similares a los "Likes" del usuario.
     */
    private List<RecommendedSong> getRecommendationsByLikedSongs(Long userId) {
        try {
            String url = communityServiceUrl + "/api/ratings/user/" + userId;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (response.getBody() == null) {
                return new ArrayList<>();
            }

            Set<Long> likedSongIds = response.getBody().stream()
                    .filter(rating -> {
                        String entityType = (String) rating.get("entityType");
                        Number ratingValue = (Number) rating.get("rating");
                        return "SONG".equals(entityType) && ratingValue != null && ratingValue.doubleValue() >= 4.0;
                    })
                    .map(rating -> {
                        Number entityId = (Number) rating.get("entityId");
                        return entityId != null ? entityId.longValue() : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (likedSongIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<Song> likedSongs = songRepository.findAllById(likedSongIds);

            Set<Long> likedGenres = likedSongs.stream()
                    .flatMap(song -> song.getGenreIds().stream())
                    .collect(Collectors.toSet());

            List<Song> recommendations = new ArrayList<>();
            for (Long genreId : likedGenres) {
                List<Song> genreSongs = songRepository.findPublishedByGenreId(genreId);
                recommendations.addAll(genreSongs.stream()
                        .filter(song -> !likedSongIds.contains(song.getId()))
                        .limit(4)
                        .toList());
            }

            List<Song> limited = recommendations.stream()
                    .distinct()
                    .limit(RECOMMENDATIONS_PER_CATEGORY)
                    .toList();

            return enrichWithArtistNames(limited, "Based on songs you liked", 0.90);

        } catch (Exception e) {
            log.warn("Error getting recommendations by liked songs for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Método auxiliar privado para enriquecer una lista de canciones con nombres de artistas y metadatos de recomendación.
     * <p>
     * Transforma entidades {@link Song} en DTOs {@link RecommendedSong}. Realiza llamadas al servicio de usuarios
     * para resolver los nombres de los artistas, utilizando un mecanismo de caché local para optimizar el rendimiento
     * y un fallback en caso de error.
     * </p>
     *
     * @param songs          Lista de entidades de canciones a enriquecer.
     * @param reason         La razón textual por la cual se recomiendan estas canciones.
     * @param relevanceScore Puntuación de relevancia (0.0 a 1.0) asignada a este grupo de recomendaciones.
     * @return Una lista de objetos {@link RecommendedSong} listos para ser consumidos por el cliente.
     */
    private List<RecommendedSong> enrichWithArtistNames(List<Song> songs, String reason, Double relevanceScore) {
        Map<Long, String> artistNamesCache = new HashMap<>();

        return songs.stream()
                .map(song -> {
                    String artistName = artistNamesCache.computeIfAbsent(
                            song.getArtistId(),
                            artistId -> {
                                try {
                                    return userServiceClient.getUserById(artistId).getArtistName();
                                } catch (Exception e) {
                                    log.debug("Failed to fetch artist name for artistId: {}, using fallback", artistId);
                                    return "Artist #" + artistId;
                                }
                            }
                    );

                    return RecommendedSong.fromSong(song, artistName, reason, relevanceScore);
                })
                .toList();
    }

    /**
     * Método utilitario para obtener el tamaño de una lista de forma segura (Null-safe).
     * <p>
     * Previene excepciones de puntero nulo al calcular totales para las estadísticas.
     * </p>
     *
     * @param list La lista de la cual se quiere saber el tamaño.
     * @return El tamaño de la lista, o 0 si la lista es nula.
     */
    private int safeListSize(List<?> list) {
        return list != null ? list.size() : 0;
    }
}
