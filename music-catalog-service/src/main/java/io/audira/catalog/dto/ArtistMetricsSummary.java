package io.audira.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO que encapsula el resumen ejecutivo de métricas para un artista.
 * <p>
 * Diseñado para alimentar el "Dashboard" principal del artista, cumpliendo con el requisito
 * <b>GA01-108: Resumen rápido</b>. Proporciona una "foto" del estado actual, incluyendo
 * acumulados históricos, rendimiento reciente (últimos 30 días) y porcentajes de crecimiento
 * comparativos (vs. periodo anterior).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistMetricsSummary {

    // --- Información Básica ---
    /** Identificador único del artista al que pertenecen las métricas. */
    private Long artistId;

    /** Nombre artístico (desnormalizado para evitar consultas extra). */
    private String artistName;

    /**
     * Fecha y hora exacta en la que se calculó este resumen.
     * <p>Útil para mostrar al usuario la frescura de los datos (ej: "Actualizado hace 5 min").</p>
     */
    private LocalDateTime generatedAt;

    // --- Métricas de Reproducción (Plays) ---
    /** Número total histórico de reproducciones de todas las canciones. */
    private Long totalPlays;

    /** Reproducciones acumuladas exclusivamente en los últimos 30 días. */
    private Long playsLast30Days;

    /**
     * Porcentaje de crecimiento de reproducciones.
     */
    private Double playsGrowthPercentage;

    // --- Métricas de Valoración (Ratings) ---
    /** Promedio de estrellas (1.0 a 5.0) de todo el catálogo del artista. */
    private Double averageRating;

    /** Cantidad total de votos recibidos. */
    private Long totalRatings;

    /** Variación porcentual de la valoración promedio respecto al mes anterior. */
    private Double ratingsGrowthPercentage;

    // --- Métricas de Ventas y Ganancias ---
    /** Número total de unidades vendidas (Canciones + Álbumes). */
    private Long totalSales;

    /** Ingresos totales históricos generados (antes de splits). */
    private BigDecimal totalRevenue;

    /** Unidades vendidas en los últimos 30 días. */
    private Long salesLast30Days;

    /** Ingresos generados en los últimos 30 días. */
    private BigDecimal revenueLast30Days;

    /** Porcentaje de crecimiento en volumen de ventas. */
    private Double salesGrowthPercentage;

    /** Porcentaje de crecimiento en ingresos monetarios. */
    private Double revenueGrowthPercentage;

    // --- Métricas de Interacción (Comentarios) ---
    /** Total histórico de comentarios recibidos. */
    private Long totalComments;

    /** Comentarios recibidos en el último mes. */
    private Long commentsLast30Days;

    /** Variación en la actividad de comentarios. */
    private Double commentsGrowthPercentage;

    // --- Métricas de Inventario de Contenido ---
    /** Número de canciones activas en el catálogo. */
    private Long totalSongs;

    /** Número de álbumes publicados. */
    private Long totalAlbums;

    /** Número de obras donde figura como colaborador invitado. */
    private Long totalCollaborations;

    // --- Contenido Destacado (Top Performing) ---
    /** ID de la canción con mayor número de reproducciones históricas. */
    private Long mostPlayedSongId;
    
    /** Título de la canción más escuchada. */
    private String mostPlayedSongName;
    
    /** Número total de reproducciones de la canción más escuchada. */
    private Long mostPlayedSongPlays;
}
