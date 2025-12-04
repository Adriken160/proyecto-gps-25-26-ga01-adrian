package io.audira.catalog.service;

import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.FeaturedContentRequest;
import io.audira.catalog.dto.FeaturedContentResponse;
import io.audira.catalog.dto.ReorderRequest;
import io.audira.catalog.dto.UserDTO;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.FeaturedContent;
import io.audira.catalog.model.FeaturedContent.ContentType;
import io.audira.catalog.model.Song;
import io.audira.catalog.repository.AlbumRepository;
import io.audira.catalog.repository.FeaturedContentRepository;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio encargado de la gestión del contenido destacado en la plataforma.
 * <p>
 * Centraliza la lógica para administrar los elementos visuales (Banners/Carruseles) que aparecen
 * en la página de inicio. Sus responsabilidades incluyen:
 * <ul>
 * <li><b>GA01-156:</b> Creación, actualización y reordenamiento manual de destacados.</li>
 * <li><b>GA01-157:</b> Validación de reglas de programación temporal (fechas de inicio y fin).</li>
 * <li><b>Desnormalización:</b> Copia de datos (título, imagen) para optimizar la lectura.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class FeaturedContentService {

    private final FeaturedContentRepository featuredContentRepository;
    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final UserServiceClient userServiceClient;
    private static final String WARNING_KEY = "Featured content not found with id: ";

    @lombok.Builder
    @lombok.Data
    private static class ContentMetadata {
        private String title;
        private String imageUrl;
        private String artist;
    }

    /**
     * Recupera la lista completa de contenido destacado, sin filtros.
     * <p>
     * Este método está diseñado para la <b>vista de administración</b>. Retorna tanto
     * el contenido activo como el inactivo, programado o expirado, ordenado por
     * su prioridad de visualización (displayOrder).
     * </p>
     *
     * @return Lista de DTOs con todos los registros de destacados.
     */
    public List<FeaturedContentResponse> getAllFeaturedContent() {
        return featuredContentRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(FeaturedContentResponse::fromEntity)
                .toList();
    }

    /**
     * Recupera únicamente el contenido destacado válido para mostrar al público.
     * <p>
     * Aplica las reglas de negocio de <b>GA01-157 (Programación)</b>:
     * <ol>
     * <li>El registro debe estar marcado como {@code active = true}.</li>
     * <li>La fecha actual debe ser posterior a {@code startDate} (si existe).</li>
     * <li>La fecha actual debe ser anterior a {@code endDate} (si existe).</li>
     * </ol>
     * </p>
     *
     * @return Lista de DTOs filtrada y ordenada para la Homepage.
     */
    public List<FeaturedContentResponse> getActiveFeaturedContent() {
        return featuredContentRepository.findActiveScheduledContent(LocalDateTime.now())
                .stream()
                .map(FeaturedContentResponse::fromEntity)
                .toList();
    }

    /**
     * Get featured content by ID
     * GA01-156
     */
    public FeaturedContentResponse getFeaturedContentById(Long id) {
        FeaturedContent entity = featuredContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));
        return FeaturedContentResponse.fromEntity(entity);
    }

    /**
     * Crea un nuevo registro de contenido destacado.
     * <p>
     * Realiza las siguientes validaciones y operaciones:
     * <ul>
     * <li>Verifica que la entidad referenciada (Canción/Álbum) realmente exista.</li>
     * <li>Evita duplicados comprobando si ese contenido ya está destacado.</li>
     * <li>"Hidrata" el registro copiando el título, imagen y artista de la entidad original
     * para evitar consultas JOIN complejas en tiempo de lectura.</li>
     * </ul>
     * </p>
     *
     * @param request DTO con los datos del contenido a destacar.
     * @return DTO del contenido destacado recién creado.
     * @throws IllegalArgumentException Si el contenido no existe o ya está destacado.
     */
    @Transactional
    public FeaturedContentResponse createFeaturedContent(FeaturedContentRequest request) {
        validateRequest(request);

        // Paso 2: Obtener metadatos según el tipo (Extraído)
        ContentMetadata metadata = resolveContentMetadata(request);

        // Paso 3: Calcular orden (Extraído)
        Integer displayOrder = calculateDisplayOrder(request.getDisplayOrder());

        FeaturedContent entity = FeaturedContent.builder()
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .displayOrder(displayOrder)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isActive(!Boolean.FALSE.equals(request.getIsActive()))
                .contentTitle(metadata.getTitle())      // Usamos el objeto metadata
                .contentImageUrl(metadata.getImageUrl())
                .contentArtist(metadata.getArtist())
                .build();

        FeaturedContent saved = featuredContentRepository.save(entity);
        return FeaturedContentResponse.fromEntity(saved);
    }

    private void validateRequest(FeaturedContentRequest request) {
        if (request.getContentType() == null) {
            throw new IllegalArgumentException("Content type is required");
        }
        if (request.getContentId() == null) {
            throw new IllegalArgumentException("Content ID is required");
        }
        if (featuredContentRepository.existsByContentTypeAndContentId(
                request.getContentType(), request.getContentId())) {
            throw new IllegalArgumentException("This content is already featured");
        }
    }

    private ContentMetadata resolveContentMetadata(FeaturedContentRequest request) {
        if (request.getContentType() == ContentType.SONG) {
            return resolveSongMetadata(request.getContentId());
        } else if (request.getContentType() == ContentType.ALBUM) {
            return resolveAlbumMetadata(request.getContentId());
        }
        // Retornamos un objeto vacío o lanzamos error si hay más tipos en el futuro
        return ContentMetadata.builder().build();
    }

    private ContentMetadata resolveSongMetadata(Long contentId) {
        Song song = songRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Song not found with id: " + contentId));

        if (!song.isPublished()) {
            throw new IllegalArgumentException("Cannot feature unpublished song");
        }

        UserDTO artistUser = userServiceClient.getUserById(song.getArtistId());
        String artistName = resolveArtistName(artistUser);

        return ContentMetadata.builder()
                .title(song.getTitle())
                .imageUrl(song.getCoverImageUrl())
                .artist(artistName)
                .build();
    }

    private ContentMetadata resolveAlbumMetadata(Long contentId) {
        Album album = albumRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found with id: " + contentId));

        if (!album.isPublished()) {
            throw new IllegalArgumentException("Cannot feature unpublished album");
        }

        UserDTO artistUser = userServiceClient.getUserById(album.getArtistId());
        String artistName = resolveArtistName(artistUser);

        return ContentMetadata.builder()
                .title(album.getTitle())
                .imageUrl(album.getCoverImageUrl())
                .artist(artistName)
                .build();
    }

    private String resolveArtistName(UserDTO artistUser) {
        if (artistUser == null) return "Unknown Artist";
        return artistUser.getArtistName() != null ? artistUser.getArtistName() : artistUser.getUsername();
    }

    private Integer calculateDisplayOrder(Integer requestOrder) {
        if (requestOrder == null || requestOrder > 900) {
            Integer maxOrder = featuredContentRepository.findMaxDisplayOrder();
            return (maxOrder != null ? maxOrder + 1 : 0);
        }
        return requestOrder;
    }

    /**
     * Actualiza un registro existente de contenido destacado.
     * <p>
     * Permite modificar la programación (fechas), el orden o incluso cambiar el contenido
     * al que apunta. Si se cambia el contenido, se vuelven a resolver los metadatos.
     * </p>
     *
     * @param id ID del registro de destacado.
     * @param request DTO con los nuevos valores.
     * @return DTO actualizado.
     * @throws IllegalArgumentException Si el registro no existe.
     */
    @Transactional
    public FeaturedContentResponse updateFeaturedContent(Long id, FeaturedContentRequest request) {
        FeaturedContent entity = featuredContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));

        if (request.getStartDate() != null) {
            entity.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            entity.setEndDate(request.getEndDate());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }

        FeaturedContent saved = featuredContentRepository.save(entity);
        return FeaturedContentResponse.fromEntity(saved);
    }

    /**
     * Elimina un elemento de la lista de destacados.
     * <p>
     * Esto solo borra la referencia en la Home; no elimina la Canción o Álbum original.
     * </p>
     *
     * @param id ID del registro de destacado a eliminar.
     */
    @Transactional
    public void deleteFeaturedContent(Long id) {
        if (!featuredContentRepository.existsById(id)) {
            throw new IllegalArgumentException(WARNING_KEY + id);
        }
        featuredContentRepository.deleteById(id);
    }

    /**
     * Reordena masivamente la lista de contenidos destacados.
     * <p>
     * Recibe una lista de pares {@code {id, nuevoOrden}} y actualiza la base de datos.
     * Esencial para la funcionalidad de "Drag & Drop" en el panel de administración.
     * </p>
     *
     * @param request DTO que contiene la lista de elementos con sus nuevas posiciones.
     * @return La lista completa de destacados con el nuevo orden aplicado.
     * @throws IllegalArgumentException Si la lista de ítems es nula o vacía.
     */
    @Transactional
    public List<FeaturedContentResponse> reorderFeaturedContent(ReorderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Items list is required for reordering");
        }

        for (ReorderRequest.ReorderItem item : request.getItems()) {
            FeaturedContent entity = featuredContentRepository.findById(item.getId())
                    .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + item.getId()));
            entity.setDisplayOrder(item.getDisplayOrder());
            featuredContentRepository.save(entity);
        }

        return getAllFeaturedContent();
    }

    /**
     * Activa o desactiva rápidamente un contenido destacado.
     * <p>
     * Método ligero (PATCH) que evita tener que enviar todo el objeto para un cambio simple de estado.
     * </p>
     *
     * @param id ID del registro.
     * @param isActive Nuevo estado booleano.
     * @return DTO con el estado actualizado.
     */
    @Transactional
    public FeaturedContentResponse toggleActive(Long id, boolean isActive) {
        FeaturedContent entity = featuredContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(WARNING_KEY + id));

        entity.setIsActive(isActive);
        FeaturedContent saved = featuredContentRepository.save(entity);
        return FeaturedContentResponse.fromEntity(saved);
    }
}
