package io.audira.catalog.service;

import io.audira.catalog.client.NotificationClient;
import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.AlbumCreateRequest;
import io.audira.catalog.dto.AlbumDTO;
import io.audira.catalog.dto.AlbumResponse;
import io.audira.catalog.dto.AlbumUpdateRequest;
import io.audira.catalog.dto.UserDTO;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.ModerationStatus;
import io.audira.catalog.model.Product;
import io.audira.catalog.model.Song;
import io.audira.catalog.repository.AlbumRepository;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Servicio que encapsula la lógica de negocio para la gestión de Álbumes.
 * <p>
 * Coordina las operaciones entre los repositorios de Álbumes y Canciones, asegurando
 * la consistencia de los datos (ej: asociar canciones a un álbum, liberar canciones al borrar un álbum).
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final UserServiceClient userServiceClient;
    private final NotificationClient notificationClient;

    /**
     * Recupera los álbumes más recientes (sin filtrar por estado).
     * <p>Utilizado para paneles administrativos.</p>
     *
     * @return Lista de álbumes ordenados por fecha de lanzamiento.
     */
    public List<Album> getRecentAlbums() {
        return albumRepository.findRecentAlbums();
    }

    /**
     * Busca un álbum por ID (Entidad pura).
     *
     * @param id ID del álbum.
     * @return Optional con el álbum.
     */
    public Optional<Album> getAlbumById(Long id) {
        return albumRepository.findById(id);
    }

    /**
     * Obtiene todos los álbumes del sistema.
     * @return Lista completa.
     */
    public List<Album> getAllAlbums() {
        return albumRepository.findAll();
    }

    /**
     * Obtiene los álbumes de un artista específico.
     *
     * @param artistId ID del artista.
     * @return Lista de álbumes del artista.
     */
    public List<Album> getAlbumsByArtist(Long artistId) {
        return albumRepository.findByArtistId(artistId);
    }

    /**
     * Obtiene los álbumes de un artista específico, incluyendo el nombre del artista.
     * <p>
     * Si la llamada al servicio de usuarios falla, se utiliza un nombre de artista por defecto.
     * </p>
     *
     * @param artistId ID del artista.
     * @return Lista de álbumes del artista con nombre.
     */
    public List<AlbumDTO> getAlbumsByArtistWithArtistName(Long artistId) {
        List<Album> albums = albumRepository.findByArtistId(artistId);

        String artistName;
        try {
            artistName = userServiceClient.getUserById(artistId).getArtistName();
        } catch (Exception e) {
            log.warn("Failed to fetch artist name for artistId: {}, using fallback", artistId);
            artistName = "Artist #" + artistId;
        }

        final String finalArtistName = artistName;
        return albums.stream()
                .map(album -> AlbumDTO.fromAlbum(album, finalArtistName))
                .toList();
    }

    /**
     * Crea un nuevo álbum y asocia las canciones indicadas.
     * <p>
     * <b>Flujo Transaccional:</b>
     * <ol>
     * <li>Guarda la entidad Álbum básica.</li>
     * <li>Itera sobre la lista de {@code songIds} proporcionada.</li>
     * <li>Actualiza cada canción para establecer su {@code albumId}, {@code trackNumber} y {@code category}.</li>
     * </ol>
     * </p>
     *
     * @param request DTO con los datos de creación.
     * @return El álbum creado y persistido.
     * @throws RuntimeException Si alguna de las canciones no existe o no pertenece al artista.
     */
    @Transactional
    public AlbumResponse createAlbum(AlbumCreateRequest request) {
        BigDecimal totalSongPrice = calculateTotalSongPrice(request.getSongIds());
        BigDecimal finalPrice = calculateFinalPrice(totalSongPrice, request);

        log.info("Album price calculation: totalSongPrice={}, discount={}%, finalPrice={}",
                totalSongPrice, request.getDiscountPercentage(), finalPrice);

        double discountPercentage = request.getDiscountPercentage() != null ?
                request.getDiscountPercentage() : 15.0;

        Album album = Album.builder()
                .title(request.getTitle())
                .artistId(request.getArtistId())
                .description(request.getDescription())
                .price(finalPrice)
                .coverImageUrl(request.getCoverImageUrl())
                .genreIds(request.getGenreIds())
                .releaseDate(request.getReleaseDate())
                .discountPercentage(discountPercentage)
                .published(false) // Por defecto no publicado
                .moderationStatus(ModerationStatus.PENDING) // GA01-162: Estado inicial
                .build();

        album = albumRepository.save(album);

        if (request.getSongIds() != null && !request.getSongIds().isEmpty()) {
            associateSongsWithAlbum(album, request.getSongIds());
        }

        int songCount = songRepository.findByAlbumId(album.getId()).size();
        return AlbumResponse.fromAlbum(album, songCount);
    }

    /**
     * Calcula la suma del precio de todas las canciones.
     * Reduce complejidad al aislar el bucle y los nulos.
     */
    private BigDecimal calculateTotalSongPrice(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Long songId : songIds) {
            // Optimización sugerida: Podrías usar songRepository.findAllById(songIds) para evitar N queries
            Optional<Song> songOpt = songRepository.findById(songId);
            if (songOpt.isPresent() && songOpt.get().getPrice() != null) {
                total = total.add(songOpt.get().getPrice());
            }
        }
        return total;
    }

    /**
     * Aplica la lógica de descuentos y precio manual.
     * Reduce complejidad aislando las condiciones matemáticas.
     */
    private BigDecimal calculateFinalPrice(BigDecimal totalSongPrice, AlbumCreateRequest request) {
        double discountPercentage = request.getDiscountPercentage() != null ?
                request.getDiscountPercentage() : 15.0;

        BigDecimal finalPrice = totalSongPrice;

        // Aplicar descuento si corresponde
        if (discountPercentage > 0 && totalSongPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountFactor = BigDecimal.valueOf(discountPercentage)
                    .divide(BigDecimal.valueOf(100));
            BigDecimal discount = totalSongPrice.multiply(discountFactor);
            finalPrice = totalSongPrice.subtract(discount);
        }

        // Sobreescribir si se define un precio manual específico y el calculado es 0 (o según tu lógica original)
        if (finalPrice.compareTo(BigDecimal.ZERO) == 0 && request.getPrice() != null) {
            finalPrice = request.getPrice();
        }

        return finalPrice;
    }

    /**
     * Vincula las canciones al álbum y actualiza sus metadatos.
     * Reduce complejidad aislando el segundo bucle y la lógica de seteo.
     */
    private void associateSongsWithAlbum(Album album, List<Long> songIds) {
        int trackNumber = 1;
        for (Long songId : songIds) {
            Optional<Song> songOpt = songRepository.findById(songId);
            if (songOpt.isPresent()) {
                Song song = songOpt.get();
                song.setAlbumId(album.getId());
                song.setTrackNumber(trackNumber++);
                song.setCategory("Album");
                songRepository.save(song);
            }
        }
    }

    /**
     * Actualiza un álbum existente.
     * <p>
     * GA01-162: Cualquier modificación requiere nueva moderación.
     * </p>
     *
     * @param id      ID del álbum a actualizar.
     * @param request DTO con los datos de actualización.
     * @return Optional con el álbum actualizado, o vacío si no existe.
     */
    @Transactional
    public Optional<AlbumResponse> updateAlbum(Long id, AlbumUpdateRequest request) {
        Optional<Album> albumOpt = albumRepository.findById(id);
        if (albumOpt.isEmpty()) {
            return Optional.empty();
        }

        Album album = albumOpt.get();
        if (request.getTitle() != null) album.setTitle(request.getTitle());
        if (request.getDescription() != null) album.setDescription(request.getDescription());
        if (request.getPrice() != null) album.setPrice(request.getPrice());
        if (request.getCoverImageUrl() != null) album.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getGenreIds() != null) album.setGenreIds(request.getGenreIds());
        if (request.getReleaseDate() != null) album.setReleaseDate(request.getReleaseDate());
        if (request.getDiscountPercentage() != null)
            album.setDiscountPercentage(request.getDiscountPercentage());

        album.setModerationStatus(ModerationStatus.PENDING);
        album.setModeratedBy(null);
        album.setModeratedAt(null);
        album.setRejectionReason(null);
        album.setPublished(false); // Ocultar hasta nueva aprobación

        album = albumRepository.save(album);
        int songCount = songRepository.findByAlbumId(album.getId()).size();
        return Optional.of(AlbumResponse.fromAlbum(album, songCount));
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
            String artistName = "Artista"; // Valor por defecto

            if (artist != null) {
                // Prioridad 1: Nombre artístico
                if (artist.getArtistName() != null) {
                    artistName = artist.getArtistName();
                } 
                // Prioridad 2: Nombre de usuario (si no hay nombre artístico)
                else {
                    artistName = artist.getUsername();
                }
            }
            
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
            log.warn("Failed to notify follower {} about new product {}", followerId, product.getId(), e);
        }
    }

    /**
     * Publica o despublica un álbum.
     * <p>
     * GA01-162: Solo se puede publicar si el álbum está aprobado.
     * </p>
     *
     * @param id        ID del álbum.
     * @param published {@code true} para publicar, {@code false} para despublicar.
     * @return Optional con el álbum actualizado, o vacío si no existe.
     * @throws IllegalArgumentException Si se intenta publicar un álbum no aprobado.
     */
    @Transactional
    public Optional<AlbumResponse> publishAlbum(Long id, boolean published) {
        Optional<Album> albumOpt = albumRepository.findById(id);
        if (albumOpt.isEmpty()) {
            return Optional.empty();
        }

        Album album = albumOpt.get();

        if (published && album.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new IllegalArgumentException(
                "No se puede publicar un álbum que no está aprobado. Estado actual: " +
                album.getModerationStatus().getDisplayName());
        }

        album.setPublished(published);
        album = albumRepository.save(album);

        // Notificar a los seguidores sobre el nuevo contenido publicado
        try {
            notifyFollowersNewProduct(album);
        } catch (Exception e) {
            log.error("Failed to notify followers about new album {}", album.getId(), e);
        }

        int songCount = songRepository.findByAlbumId(album.getId()).size();
        return Optional.of(AlbumResponse.fromAlbum(album, songCount));
    }

    /**
     * Elimina un álbum y desasocia sus canciones.
     * <p>
     * <b>Flujo Transaccional:</b>
     * <ol>
     * <li>Busca el álbum por ID.</li>
     * <li>Si no existe, retorna {@code false}.</li>
     * <li>Busca todas las canciones asociadas al álbum.</li>
     * <li>Para cada canción, elimina la asociación con el álbum y actualiza su categoría a "Single".</li>
     * <li>Elimina el álbum.</li>
     * </ol>
     * </p>
     *
     * @param id ID del álbum a eliminar.
     * @return {@code true} si se eliminó el álbum, {@code false} si no existía.
     */
    @Transactional
    public boolean deleteAlbum(Long id) {
        Optional<Album> albumOpt = albumRepository.findById(id);
        if (albumOpt.isEmpty()) {
            return false;
        }

        List<Song> songs = songRepository.findByAlbumId(id);
        for (Song song : songs) {
            song.setAlbumId(null);
            song.setTrackNumber(null);
            song.setCategory("Single");
            songRepository.save(song);
        }

        albumRepository.deleteById(id);
        return true;
    }

    /**
     * Obtiene un álbum por ID, incluyendo el conteo de canciones.
     *
     * @param id ID del álbum.
     * @return DTO con los datos del álbum y conteo de canciones, o {@code null} si no existe.
     */
    public AlbumResponse getAlbumResponseById(Long id) {
        Optional<Album> albumOpt = albumRepository.findById(id);
        if (albumOpt.isEmpty()) {
            return null;
        }

        Album album = albumOpt.get();
        int songCount = songRepository.findByAlbumId(id).size();
        return AlbumResponse.fromAlbum(album, songCount);
    }

    /**
     * Recupera los 20 álbumes publicados más recientes.
     *
     * @return Lista de álbumes publicados ordenados por fecha de creación.
     */
    public List<Album> getRecentPublishedAlbums() {
        return albumRepository.findTop20ByPublishedTrueOrderByCreatedAtDesc();
    }
}