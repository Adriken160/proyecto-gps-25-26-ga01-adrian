package io.audira.catalog.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad base abstracta que representa cualquier producto comercializable o consumible en el catálogo.
 * <p>
 * Utiliza la estrategia de herencia <b>{@code JOINED}</b>, lo que significa que habrá una tabla {@code products}
 * con los campos comunes, y tablas separadas ({@code songs}, {@code albums}) para los campos específicos,
 * unidas por la clave primaria.
 * </p>
 * <p>
 * Incluye configuración de <b>Jackson</b> ({@code @JsonTypeInfo}) para permitir el polimorfismo en las APIs REST:
 * el campo {@code "productType"} en el JSON determinará si se instancia un {@link Song} o un {@link Album}.
 * </p>
 */
@Entity
@Table(name = "products")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "product_type", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "productType", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Song.class, name = "SONG"),
    @JsonSubTypes.Type(value = Album.class, name = "ALBUM")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Product {

    /** Identificador único del producto. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Título de la obra. */
    @Column(nullable = false)
    private String title;

    /** ID del artista propietario. */
    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    /** Precio base de venta. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * URL de la imagen de portada.
     * <p>Admite alias {@code coverUrl} en el JSON para compatibilidad con clientes legacy.</p>
     */
    @Column(name = "cover_image_url")
    @JsonAlias({"coverUrl", "cover_url"})
    private String coverImageUrl;

    /** Descripción o sinopsis del producto. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Fecha de creación del registro. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Fecha de última actualización. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- GA01-162: Campos de Moderación ---

    /**
     * Estado actual en el flujo de aprobación.
     * <p>Define si el producto es visible para el público.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false)
    private ModerationStatus moderationStatus;

    /**
     * Razón del rechazo (si el estado es {@code REJECTED}).
     * <p>Mensaje de feedback para el artista.</p>
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /** ID del administrador que realizó la última acción de moderación. */
    @Column(name = "moderated_by")
    private Long moderatedBy;

    /** Fecha de la última moderación. */
    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    /**
     * Callback JPA ejecutado antes de persistir.
     * <p>Establece fechas de auditoría y el estado de moderación inicial (PENDING).</p>
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.moderationStatus == null) {
            this.moderationStatus = ModerationStatus.PENDING;
        }
    }

    /**
     * Callback JPA ejecutado antes de actualizar.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Método abstracto para obtener el discriminador del tipo de producto.
     * @return "SONG" o "ALBUM".
     */
    public abstract String getProductType();
}
