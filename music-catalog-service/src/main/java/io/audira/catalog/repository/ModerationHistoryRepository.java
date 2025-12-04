package io.audira.catalog.repository;

import io.audira.catalog.model.ModerationHistory;
import io.audira.catalog.model.ModerationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio JPA para la entidad {@link ModerationHistory}.
 * <p>
 * Gestiona el registro de auditoría de moderación (GA01-163).
 * Incluye consultas para visualizar el historial y generar reportes estadísticos
 * sobre la actividad de los administradores.
 * </p>
 */
@Repository
public interface ModerationHistoryRepository extends JpaRepository<ModerationHistory, Long> {

    /**
     * Obtiene el historial global de moderaciones ordenado cronológicamente.
     * @return Lista completa, más reciente primero.
     */
    List<ModerationHistory> findAllByOrderByModeratedAtDesc();

    /**
     * Obtiene el historial específico de un producto (Canción o Álbum).
     * <p>Permite ver la "vida" de un producto a través del proceso de revisión.</p>
     *
     * @param productId ID del producto.
     * @param productType Tipo ("SONG" o "ALBUM").
     * @return Historial del producto.
     */
    List<ModerationHistory> findByProductIdAndProductTypeOrderByModeratedAtDesc(
            Long productId, String productType);

    /**
     * @param artistId ID del artista.
     * @return Historial del artista.
     */
    List<ModerationHistory> findByArtistIdOrderByModeratedAtDesc(Long artistId);

    /**
     * Obtiene las acciones realizadas por un administrador específico.
     * <p>Útil para auditorías internas de desempeño.</p>
     * @param moderatedBy ID del administrador.
     * @return Lista de acciones realizadas.
     */
    List<ModerationHistory> findByModeratedByOrderByModeratedAtDesc(Long moderatedBy);

    /**
     * Filtra el historial por el resultado de la moderación (ej: ver todos los rechazos).
     * @param newStatus Estado asignado.
     * @return Lista filtrada.
     */
    List<ModerationHistory> findByNewStatusOrderByModeratedAtDesc(ModerationStatus newStatus);

    /**
     * Obtiene las moderaciones realizadas dentro de un rango de fechas.
     * @param startDate Inicio del periodo.
     * @param endDate Fin del periodo.
     * @return Lista de eventos en ese rango.
     */
    List<ModerationHistory> findByModeratedAtBetweenOrderByModeratedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Genera estadísticas de volumen de moderación por estado.
     * <p>Ej: Cuántos aprobados vs rechazados.</p>
     *
     * @return Lista de arrays donde [0]=Estado, [1]=Cantidad(Long).
     */
    @Query("SELECT m.newStatus, COUNT(m) FROM ModerationHistory m GROUP BY m.newStatus")
    List<Object[]> getModerationStatistics();

    /**
     * Genera un ranking de actividad de moderadores (Leaderboard).
     * <p>Muestra qué administradores han realizado más acciones.</p>
     *
     * @return Lista de arrays donde [0]=AdminId, [1]=NombreAdmin, [2]=CantidadAcciones.
     */
    @Query("SELECT m.moderatedBy, m.moderatorName, COUNT(m) FROM ModerationHistory m " +
           "GROUP BY m.moderatedBy, m.moderatorName ORDER BY COUNT(m) DESC")
    List<Object[]> getModerationsByAdmin();

    /**
     * Obtiene una lista limitada de las últimas N moderaciones.
     * <p>Nota: Este método usa una consulta nativa o derivada limitada en la implementación de servicio,
     * aquí se define la firma base.</p>
     *
     * @return Lista de las 10 moderaciones más recientes.
     */
    List<ModerationHistory> findAllByOrderByModeratedAtDesc(Pageable pageable);
}
