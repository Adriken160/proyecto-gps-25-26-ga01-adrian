package io.audira.catalog.repository;

import io.audira.catalog.model.FeaturedContent;
import io.audira.catalog.model.FeaturedContent.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link FeaturedContent}.
 * <p>
 * Gestiona el contenido del carrusel de inicio. Soporta:
 * <ul>
 * <li><b>GA01-156:</b> Ordenamiento manual (displayOrder).</li>
 * <li><b>GA01-157:</b> Lógica de programación temporal (fechas de inicio/fin).</li>
 * </ul>
 * </p>
 */
@Repository
public interface FeaturedContentRepository extends JpaRepository<FeaturedContent, Long> {

    /**
     * Recupera todo el contenido destacado ordenado por prioridad.
     *
     * @return Lista completa ordenada.
     */
    List<FeaturedContent> findAllByOrderByDisplayOrderAsc();

    /**
     * Encuentra el contenido destacado que debe mostrarse públicamente en este momento.
     * <p>
     * Aplica la lógica de programación <b>GA01-157</b>:
     * <ol>
     * <li>El flag {@code isActive} debe ser {@code true}.</li>
     * <li>La fecha de inicio debe ser nula (inmediato) o anterior/igual a {@code now}.</li>
     * <li>La fecha de fin debe ser nula (indefinido) o posterior/igual a {@code now}.</li>
     * </ol>
     * Los resultados se ordenan por {@code displayOrder}.
     * </p>
     *
     * @param now La fecha y hora actual (normalmente {@code LocalDateTime.now()}).
     * @return Lista de contenido visible actualmente.
     */
    @Query("SELECT fc FROM FeaturedContent fc WHERE fc.isActive = true " +
           "AND (fc.startDate IS NULL OR fc.startDate <= :now) " +
           "AND (fc.endDate IS NULL OR fc.endDate >= :now) " +
           "ORDER BY fc.displayOrder ASC")
    List<FeaturedContent> findActiveScheduledContent(@Param("now") LocalDateTime now);

    /**
     * Busca un registro específico por tipo y ID de contenido.
     * @param contentType Tipo (SONG, ALBUM, etc.).
     * @param contentId ID de la entidad.
     * @return Optional con el registro si existe.
     */
    Optional<FeaturedContent> findByContentTypeAndContentId(ContentType contentType, Long contentId);

    /**
     * Verifica si una entidad ya está destacada.
     * <p>Utilizado para evitar duplicados en el carrusel.</p>
     */
    boolean existsByContentTypeAndContentId(ContentType contentType, Long contentId);

    /**
     * @return Lista de contenido activo ordenada.
     */
    List<FeaturedContent> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * @param contentType Tipo de contenido (SONG, ALBUM, etc.).
     * @return Lista filtrada y ordenada.
     */
    List<FeaturedContent> findByContentTypeOrderByDisplayOrderAsc(ContentType contentType);

    /**
     * Encuentra el mayor valor de {@code displayOrder} actualmente en uso.
     * <p>Utilizado para asignar el siguiente orden al agregar nuevo contenido.</p>
     *
     * @return El valor máximo de {@code displayOrder}, o {@code null} si no hay registros.
     */
    @Query("SELECT MAX(fc.displayOrder) FROM FeaturedContent fc")
    Integer findMaxDisplayOrder();
}
