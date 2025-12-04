package io.audira.catalog.service;

import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.SongDTO;
import io.audira.catalog.dto.UserDTO;
import io.audira.catalog.model.ModerationStatus;
import io.audira.catalog.model.Product;
import io.audira.catalog.model.Song;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import io.audira.catalog.client.NotificationClient;

/**
 * Servicio central para la gestión del inventario de canciones (Tracks).
 * <p>
 * Encapsula toda la lógica de negocio relacionada con las canciones individuales:
 * <ul>
 * <li>Validación y creación de registros.</li>
 * <li>Gestión del ciclo de vida (Publicación/Moderación).</li>
 * <li>Lógica de consumo (Conteo de reproducciones).</li>
 * <li>Enriquecimiento de datos (DTOs) integrando información del {@code UserServiceClient}.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SongService {

    private final SongRepository songRepository;
    private final UserServiceClient userServiceClient;
    private final NotificationClient notificationClient;
    private static final String WARNING_KEY = "Song not found with id: ";

    /**
     * Registra una nueva canción en el sistema.
     * <p>
     * Aplica validaciones estrictas sobre los campos obligatorios antes de persistir.
     * Establece el estado de moderación inicial como {@code PENDING} y la visibilidad pública en {@code false}.
     * </p>
     *
     * @param song La entidad {@link Song} con los datos crudos recibidos.
     * @return La canción persistida con ID generado y timestamps de auditoría.
     * @throws IllegalArgumentException Si faltan campos requeridos (Título, Artista, Duración, Géneros).
     */
    @Transactional
    public Song createSong(Song song) {
        if (song.getTitle() == null || song.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Song title is required");
        }
        if (song.getArtistId() == null) {
            throw new IllegalArgumentException("Artist ID is required");
        }
        if (song.getDuration() == null || song.getDuration() <= 0) {
            throw new IllegalArgumentException("Valid duration is required");
        }
        if (song.getGenreIds() == null || song.getGenreIds().isEmpty()) {
            throw new IllegalArgumentException("At least one genre is required");
        }

        // GA01-162: Forzar estado inicial de moderación
        // Todas las canciones nuevas deben estar en PENDING y ocultas
        song.setModerationStatus(ModerationStatus.PENDING);
        song.setPublished(false);
        song.setModeratedBy(null);
        song.setModeratedAt(null);
        song.setRejectionReason(null);

        Song savedSong = songRepository.save(song);

        try {
            // Se asume un ID de administrador para notificación (ej: 70L, debe ser configurable)
            Long adminId = 70L; 

            // Se busca el nombre del artista para el mensaje de notificación
            String artistName = userServiceClient.getUserById(savedSong.getArtistId()).getArtistName();

            notificationClient.notifyAdminPendingReview(
                adminId, // ID del administrador (debe ser configurado)
                "SONG",
                savedSong.getTitle(),
                artistName
            );
        } catch (Exception e) {
            log.error("Failed to send pending review notification for song {}", savedSong.getId(), e);
        }

        return songRepository.save(song);
    }

    /**
     * Recupera el listado completo de todas las canciones en el sistema (Entidades puras).
     * <p>
     * Incluye contenido en borrador, pendiente y rechazado.
     * </p>
     *
     * @return Lista completa de entidades {@link Song}.
     */
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    /**
     * Busca una canción por su identificador único (Entidad pura).
     *
     * @param id ID de la canción.
     * @return La entidad {@link Song}.
     * @throws IllegalArgumentException Si la canción no existe.
     */
    public Song getSongById(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));
    }

    /**
     * Obtiene todas las canciones de un artista (Entidades puras).
     *
     * @param artistId ID del artista.
     * @return Lista de canciones del artista.
     */
    public List<Song> getSongsByArtist(Long artistId) {
        return songRepository.findByArtistId(artistId);
    }

    /**
     * Obtiene todas las canciones de un artista con el nombre del artista (DTOs).
     *
     * @param artistId ID del artista.
     * @return Lista de DTOs de canciones del artista.
     */
    public List<SongDTO> getSongsByArtistWithArtistName(Long artistId) {
        List<Song> songs = songRepository.findByArtistId(artistId);

        // Get artist name once
        String artistName;
        try {
            artistName = userServiceClient.getUserById(artistId).getArtistName();
        } catch (Exception e) {
            log.warn("Failed to fetch artist name for artistId: {}, using fallback", artistId);
            artistName = "Artist #" + artistId;
        }

        // Convert all songs to DTOs with the artist name
        final String finalArtistName = artistName;
        return songs.stream()
                .map(song -> SongDTO.fromSong(song, finalArtistName))
                .toList();
    }

    /**
     * Obtiene todas las canciones de un álbum (Entidades puras).
     *
     * @param albumId ID del álbum.
     * @return Lista de canciones asociadas al álbum.
     */
    public List<Song> getSongsByAlbum(Long albumId) {
        return songRepository.findByAlbumId(albumId);
    }

    /**
     * Obtiene todas las canciones de un género específico (Entidades puras).
     *
     * @param genreId ID del género.
     * @return Lista de canciones clasificadas en ese género.
     */
    public List<Song> getSongsByGenre(Long genreId) {
        return songRepository.findByGenreId(genreId);
    }

    /**
     * Recupera las canciones más recientes subidas al sistema.
     * <p>
     * Ordenadas por fecha de creación descendente, sin filtros de estado.
     * </p>
     *
     * @return Lista de canciones recientes.
     */
    public List<Song> getRecentSongs() {
        return songRepository.findTop20ByOrderByCreatedAtDesc();
    }

    /**
     * Recupera el ranking global de canciones por número de reproducciones.
     * <p>
     * Incluye todas las canciones, independientemente de si están publicadas actualmente.
     * </p>
     *
     * @return Lista de canciones ordenadas por popularidad.
     */
    public List<Song> getTopSongsByPlays() {
        return songRepository.findTopByPlays();
    }

    /**
     * Busca canciones por coincidencia de texto en título o artista.
     * <p>
     * Búsqueda administrativa sobre todo el catálogo.
     * </p>
     *
     * @param query Texto de búsqueda.
     * @return Lista de resultados coincidentes.
     */
    public List<Song> searchSongs(String query) {
        return songRepository.searchByTitleOrArtist(query);
    }

    /**
     * Actualiza los metadatos de una canción existente.
     * <p>
     * Solo modifica los campos que no sean nulos en el objeto recibido.
     * Si se detectan cambios sensibles, podría resetear el estado de moderación (lógica pendiente).
     * </p>
     *
     * @param id ID de la canción a modificar.
     * @param songDetails Objeto con los nuevos valores.
     * @return La canción actualizada.
     * @throws IllegalArgumentException Si la canción no existe.
     */
    @Transactional
    public Song updateSong(Long id, Song songDetails) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));

        if (songDetails.getTitle() != null) {
            song.setTitle(songDetails.getTitle());
        }
        if (songDetails.getDescription() != null) {
            song.setDescription(songDetails.getDescription());
        }
        if (songDetails.getPrice() != null) {
            song.setPrice(songDetails.getPrice());
        }
        if (songDetails.getCoverImageUrl() != null) {
            song.setCoverImageUrl(songDetails.getCoverImageUrl());
        }
        if (songDetails.getAudioUrl() != null) {
            song.setAudioUrl(songDetails.getAudioUrl());
        }
        if (songDetails.getLyrics() != null) {
            song.setLyrics(songDetails.getLyrics());
        }
        if (songDetails.getDuration() != null) {
            song.setDuration(songDetails.getDuration());
        }
        if (songDetails.getGenreIds() != null && !songDetails.getGenreIds().isEmpty()) {
            song.setGenreIds(songDetails.getGenreIds());
        }
        if (songDetails.getCategory() != null) {
            song.setCategory(songDetails.getCategory());
        }
        if (songDetails.getAlbumId() != null) {
            song.setAlbumId(songDetails.getAlbumId());
        }
        if (songDetails.getTrackNumber() != null) {
            song.setTrackNumber(songDetails.getTrackNumber());
        }

        // GA01-162: Cualquier modificación requiere nueva moderación
        song.setModerationStatus(ModerationStatus.PENDING);
        song.setModeratedBy(null);
        song.setModeratedAt(null);
        song.setRejectionReason(null);
        song.setPublished(false); // Ocultar hasta nueva aprobación

        return songRepository.save(song);
    }

    /**
     * Elimina una canción del sistema.
     * <p>
     * <b>Nota:</b> En un entorno de producción, esto debería disparar eventos para limpiar
     * referencias en playlists y archivos físicos en FileService.
     * </p>
     *
     * @param id ID de la canción a eliminar.
     */
    @Transactional
    public void deleteSong(Long id) {
        if (!songRepository.existsById(id)) {
            throw new IllegalArgumentException(WARNING_KEY + id);
        }
        songRepository.deleteById(id);
    }

    /**
     * Incrementa el contador de reproducciones de una canción.
     * <p>
     * Método atómico llamado cada vez que un usuario escucha la canción por más de 30 segundos.
     * Fundamental para el cálculo de tendencias y regalías.
     * </p>
     *
     * @param id ID de la canción.
     * @return La canción con el contador actualizado.
     * @throws IllegalArgumentException Si la canción no existe.
     */
    @Transactional
    public Song incrementPlays(Long id) {
        Song song = getSongById(id);
        song.setPlays(song.getPlays() + 1);
        return songRepository.save(song);
    }

    /**
     * Método auxiliar privado para notificar a los seguidores sobre un nuevo lanzamiento.
     * <p>
     * Se ejecuta tras la aprobación exitosa de un contenido. Obtiene la lista de seguidores
     * del artista y envía una notificación individual a cada uno.
     * </p>
     *
     * @param product El producto (Canción o Álbum) que acaba de ser publicado.
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

    /**
     * Modifica el estado de publicación de una canción.
     * <p>
     * <b>Regla de Negocio:</b> Solo se puede publicar ({@code published = true}) una canción
     * si su estado de moderación es {@code APPROVED}.
     * </p>
     *
     * @param id ID de la canción.
     * @param published Nuevo estado de visibilidad.
     * @return La canción actualizada.
     * @throws IllegalArgumentException Si la canción no existe o no está aprobada.
     */
    @Transactional
    public Song publishSong(Long id, boolean published) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));

        // GA01-162: Solo se puede publicar si está aprobada
        if (published && song.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new IllegalArgumentException(
                "No se puede publicar una canción que no está aprobada. Estado actual: " +
                song.getModerationStatus().getDisplayName());
        }

        song.setPublished(published);

        // Notificar a los seguidores sobre el nuevo contenido publicado
        try {
            notifyFollowersNewProduct(song);
        } catch (Exception e) {
            log.error("Failed to notify followers about new song {}", song.getId(), e);
        }
        return songRepository.save(song);
    }

    /**
     * Obtiene los lanzamientos más recientes que están publicados.
     * <p>
     * Filtra explícitamente por {@code published = true} y estado aprobado.
     * Utilizado para la sección "Novedades" de la Home.
     * </p>
     *
     * @return Lista de DTOs visibles para el público.
     */
    public List<Song> getRecentPublishedSongs() {
        return songRepository.findTop20ByPublishedTrueOrderByCreatedAtDesc();
    }

    /**
     * Obtiene el ranking de las canciones más escuchadas que están publicadas.
     * <p>
     * Top Charts visible para los usuarios finales.
     * </p>
     *
     * @return Lista de DTOs de canciones populares.
     */
    public List<Song> getTopPublishedSongsByPlays() {
        return songRepository.findTopPublishedByPlays();
    }
    
    /**
     * Busca canciones publicadas por coincidencia de texto.
     * <p>
     * Motor de búsqueda para usuarios finales. Solo retorna contenido validado y público.
     * </p>
     *
     * @param query Texto a buscar en título o nombre de artista.
     * @return Resultados de búsqueda públicos.
     */
    public List<Song> searchPublishedSongs(String query) {
        return songRepository.searchPublishedByTitleOrArtist(query);
    }

    /**
     * Obtiene canciones publicadas de un género específico.
     * <p>
     * Utilizado al navegar por categorías (ej: "Explorar Jazz").
     * </p>
     *
     * @param genreId ID del género.
     * @return Lista de canciones visibles del género.
     */
    public List<Song> getPublishedSongsByGenre(Long genreId) {
       return songRepository.findPublishedByGenreId(genreId);
    }
     
    /**
     * Convierte una entidad {@link Song} a un {@link SongDTO}.
     * <p>
     * Realiza una llamada síncrona al {@code UserServiceClient} para obtener el nombre real del artista.
     * Si la llamada falla, utiliza un nombre de respaldo ("Artist #ID").
     * </p>
     *
     * @param song La entidad a convertir.
     * @return El DTO enriquecido.
     */
    private SongDTO convertToDTO(Song song) {
        try {
            String fetchedName = userServiceClient.getUserById(song.getArtistId()).getArtistName();
            
            song.setArtistName(fetchedName);
            
        } catch (Exception e) {
            log.warn("Failed to fetch artist name for artistId: {}, using fallback", song.getArtistId());
            song.setArtistName("Artista #" + song.getArtistId());
        }

        if (song.getArtistName() == null || song.getArtistName().trim().isEmpty()) {
            song.setArtistName("Artista #" + song.getArtistId());
        }

        return SongDTO.fromSong(song, song.getArtistName());
    }
 
    /**
     * Convierte una lista de entidades a una lista de DTOs.
     * <p>
     * Aplica la conversión individual {@link #convertToDTO} a cada elemento.
     * </p>
     *
     * @param songs Lista de entidades.
     * @return Lista de DTOs.
     */
    private List<SongDTO> convertToDTOs(List<Song> songs) {
        return songs.stream()
                .map(this::convertToDTO)
                .toList();
    }
 
    /**
     * Obtiene el listado completo de canciones registradas en el sistema.
     * <p>
     * <b>Nota:</b> Incluye canciones en estado de borrador, rechazadas o pendientes.
     * Uso exclusivo para paneles de administración o gestión interna del artista.
     * </p>
     *
     * @return Lista de DTOs enriquecidos con el nombre del artista.
     */
    public List<SongDTO> getAllSongsWithArtistName() {
        return convertToDTOs(songRepository.findAll());
    }
 
    /**
     * Obtiene el detalle de una canción específica por su ID.
     *
     * @param id Identificador único de la canción.
     * @return DTO enriquecido con el detalle.
     * @throws IllegalArgumentException Si la canción no existe.
     */
    public SongDTO getSongByIdWithArtistName(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));
        return convertToDTO(song);
    }
 
    /**
     * Obtiene todas las canciones asociadas a un álbum.
     *
     * @param albumId ID del álbum.
     * @return Lista de canciones del álbum.
     */
    public List<SongDTO> getSongsByAlbumWithArtistName(Long albumId) {
        return convertToDTOs(songRepository.findByAlbumId(albumId));
    }
 
    /**
     * Obtiene todas las canciones clasificadas bajo un género específico.
     *
     * @param genreId ID del género musical.
     * @return Lista de canciones del género.
     */
    public List<SongDTO> getSongsByGenreWithArtistName(Long genreId) {
        return convertToDTOs(songRepository.findByGenreId(genreId));
    }
 
    /**
     * Recupera las canciones más recientes (Vista administrativa).
     *
     * @return Lista de las últimas 20 canciones subidas, sin importar su estado.
     */
    public List<SongDTO> getRecentSongsWithArtistName() {
        return convertToDTOs(songRepository.findTop20ByOrderByCreatedAtDesc());
    }
 
    /**
     * Recupera el ranking de canciones más escuchadas (Vista administrativa).
     *
     * @return Lista de canciones ordenadas por plays, sin importar su estado.
     */
    public List<SongDTO> getTopSongsByPlaysWithArtistName() {
        return convertToDTOs(songRepository.findTopByPlays());
    }

    /**
     * Busca canciones por título o artista (Vista administrativa).
     *
     * @param query Texto de búsqueda.
     * @return Resultados coincidentes sin filtro de publicación.
     */
    public List<SongDTO> searchSongsWithArtistName(String query) {
        return convertToDTOs(songRepository.searchByTitleOrArtist(query));
    }

    /**
     * Recupera los lanzamientos más recientes que están publicados.
     * <p>
     * Filtra explícitamente por {@code published = true} y estado aprobado.
     * Utilizado para la sección "Novedades" de la Home.
     * </p>
     *
     * @return Lista de DTOs visibles para el público.
     */
    public List<SongDTO> getRecentPublishedSongsWithArtistName() {
        return convertToDTOs(songRepository.findTop20ByPublishedTrueOrderByCreatedAtDesc());
    }

    /**
     * Recupera el ranking de canciones más populares que están publicadas.
     * <p>
     * Utilizado para los "Top Charts" o listas de éxitos.
     * </p>
     *
     * @return Lista de DTOs ordenados por reproducciones.
     */
    public List<SongDTO> getTopPublishedSongsByPlaysWithArtistName() {
        return convertToDTOs(songRepository.findTopPublishedByPlays());
    }

    /**
     * Busca canciones publicadas por coincidencia de texto.
     * <p>
     * Motor de búsqueda para usuarios finales. Solo retorna contenido validado y público.
     * </p>
     *
     * @param query Texto a buscar en título o nombre de artista.
     * @return Resultados de búsqueda públicos.
     */
    public List<SongDTO> searchPublishedSongsWithArtistName(String query) {
        return convertToDTOs(songRepository.searchPublishedByTitleOrArtist(query));
    }

    /**
     * Obtiene canciones publicadas de un género específico.
     * <p>
     * Utilizado al navegar por categorías (ej: "Explorar Jazz").
     * </p>
     *
     * @param genreId ID del género.
     * @return Lista de canciones visibles del género.
     */
    public List<SongDTO> getPublishedSongsByGenreWithArtistName(Long genreId) {
        return convertToDTOs(songRepository.findPublishedByGenreId(genreId));
    }

    /**
     * Obtiene el ID del artista y el precio de una canción específica.
     * <p>
     * Este método está diseñado para ser consumido por el microservicio de Comercio.
     * Solo recupera la información mínima necesaria para las transacciones (artistId, price).
     * </p>
     *
     * @param id ID de la canción.
     * @return Un Map con "artistId" y "price".
     * @throws IllegalArgumentException Si la canción no existe.
     */
    public Map<String, Object> getArtistAndPriceBySongId(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));

        return Map.of(
                "artistId", song.getArtistId(),
                "price", song.getPrice()
        );
    }
}
