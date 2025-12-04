package io.audira.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.LocalDateTime;

/**
 * Entidad que gestiona el contenido destacado (Carrusel/Banners) en la página de inicio.
 * <p>
 * Permite referenciar cualquier tipo de entidad (Canción, Álbum, Playlist) de forma polimórfica
 * y controlar su visualización mediante fechas y prioridades.
 * </p>
 * <ul>
 * <li><b>GA01-156:</b> Selección y ordenación manual.</li>
 * <li><b>GA01-157:</b> Programación temporal (Scheduling).</li>
 * </ul>
 */
@Entity
@Table(
    name = "featured_content",
    uniqueConstraints = @UniqueConstraint(columnNames = {"content_type", "content_id"}),
    indexes = {
        @Index(name = "idx_active", columnList = "is_active"),
        @Index(name = "idx_dates", columnList = "start_date, end_date"),
        @Index(name = "idx_order", columnList = "display_order")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedContent {

    /** Identificador único del registro de destacado. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de contenido referenciado.
     * <p>Determina en qué tabla buscar el {@code contentId}.</p>
     */
    @Column(name = "content_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    /**
     * ID de la entidad referenciada (Song ID, Album ID, etc.).
     */
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    // Campos desnormalizados para evitar JOINS costosos al renderizar la Home

    /** Título copiado de la entidad original. */
    @Column(name = "content_title")
    private String contentTitle;

    /** URL de la imagen copiada de la entidad original. */
    @Column(name = "content_image_url")
    private String contentImageUrl;

    /** Nombre del artista copiado de la entidad original. */
    @Column(name = "content_artist")
    private String contentArtist;

    /**
     * Prioridad de ordenamiento visual.
     * <p>Los valores numéricos más bajos se muestran primero (izquierda/arriba).</p>
     */
    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Fecha de inicio de la promoción (Opcional).
     * <p>Si es nula, el contenido se destaca inmediatamente (siempre que {@code isActive} sea true).</p>
     */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /**
     * Fecha de fin de la promoción (Opcional).
     * <p>Si es nula, el contenido se destaca indefinidamente.</p>
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * Interruptor maestro de visibilidad.
     * <p>Permite ocultar un contenido sin borrar el registro ni perder la configuración de fechas.</p>
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Fecha de creación del registro. */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Fecha de última modificación. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Enumeración interna para los tipos de contenido soportados en el carrusel.
     */
    public enum ContentType {
        SONG, ALBUM, ARTIST, PLAYLIST;

        /**
         * Método factoría para deserializar JSON insensible a mayúsculas/minúsculas.
         *
         * @param value Cadena de texto (ej: "song", "Song").
         * @return La constante del enum correspondiente.
         */
        @JsonCreator
        public static ContentType fromString(String value) {
            if (value == null) {
                return null;
            }
            return ContentType.valueOf(value.toUpperCase());
        }
    }

    /**
     * Lógica de negocio para determinar si el contenido debe mostrarse <b>ahora mismo</b>.
     * <p>
     * Evalúa tres condiciones:
     * <ol>
     * <li>El flag {@code isActive} debe ser true.</li>
     * <li>Si existe {@code startDate}, la fecha actual debe ser posterior.</li>
     * <li>Si existe {@code endDate}, la fecha actual debe ser anterior.</li>
     * </ol>
     * </p>
     *
     * @return {@code true} si el contenido es visible actualmente.
     */
    @Transient
    public boolean isScheduledActive() {
        LocalDateTime now = LocalDateTime.now();

        return Boolean.TRUE.equals(isActive)
                && (startDate == null || !now.isBefore(startDate))
                && (endDate == null || !now.isAfter(endDate));
    }

    /**
     * Genera un estado textual legible para la interfaz de administración.
     *
     * @return "INACTIVE" (apagado manual), "SCHEDULED" (pendiente de fecha),
     * "EXPIRED" (fecha pasada) o "ACTIVE" (visible).
     */
    @Transient
    public String getScheduleStatus() {
        if (!Boolean.TRUE.equals(isActive)) { 
            return "INACTIVE";
        }

        LocalDateTime now = LocalDateTime.now();

        if (startDate != null && now.isBefore(startDate)) {
            return "SCHEDULED";
        }

        if (endDate != null && now.isAfter(endDate)) {
            return "EXPIRED";
        }

        return "ACTIVE";
    }
}
