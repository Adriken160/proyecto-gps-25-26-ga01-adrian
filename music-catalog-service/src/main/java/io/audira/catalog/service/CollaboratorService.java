package io.audira.catalog.service;

import io.audira.catalog.dto.CollaborationRequest;
import io.audira.catalog.dto.UpdateRevenueRequest;
import io.audira.catalog.model.CollaborationStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.audira.catalog.model.Collaborator;
import io.audira.catalog.repository.CollaboratorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Servicio encargado de la lógica de negocio para la gestión de colaboradores y reparto de regalías.
 * <p>
 * Centraliza todas las operaciones relacionadas con:
 * <ul>
 * <li><b>GA01-154:</b> Gestión del ciclo de vida de las colaboraciones (Invitación -> Aceptación/Rechazo).</li>
 * <li><b>GA01-155:</b> Cálculo y validación de porcentajes de ingresos (Revenue Splits).</li>
 * </ul>
 * Maneja la integridad de datos tanto para colaboraciones en Canciones individuales como en Álbumes completos.
 * </p>
 */
@RequiredArgsConstructor
@Service
public class CollaboratorService {

    private static final Logger logger = LoggerFactory.getLogger(CollaboratorService.class);
    private static final String ALBUM_KEY = "album";
    private static final String SONG_KEY = "song";
    private static final String WARNING_KEY = "Collaboration not found with id: ";

    private final CollaboratorRepository collaboratorRepository;

    /**
     * Recupera el listado completo de todas las colaboraciones registradas en el sistema.
     * <p>Utilizado principalmente para propósitos administrativos o de auditoría global.</p>
     *
     * @return Lista completa de entidades {@link Collaborator}.
     */
    public List<Collaborator> getAllCollaborators() {
        return collaboratorRepository.findAll();
    }

    /**
     * Busca una colaboración específica por su identificador único.
     *
     * @param id ID de la colaboración.
     * @return Un {@link Optional} que contiene la colaboración si existe.
     */
    public Optional<Collaborator> getCollaboratorById(Long id) {
        return collaboratorRepository.findById(id);
    }

    /**
     * Obtiene todos los colaboradores asociados a una canción, independientemente de su estado.
     *
     * @param songId ID de la canción.
     * @return Lista de colaboradores (pendientes, aceptados y rechazados).
     */
    public List<Collaborator> getCollaboratorsBySongId(Long songId) {
        return collaboratorRepository.findBySongId(songId);
    }
    
    /**
     * Obtiene todos los colaboradores asociados a un artista, independientemente de su estado.
     *
     * @param artistId ID del artista.
     * @return Lista de colaboradores del artista.
     */
    public List<Collaborator> getCollaboratorsByArtistId(Long artistId) {
        return collaboratorRepository.findByArtistId(artistId);
    }

    /**
     * Crea un registro de colaborador directamente (Uso administrativo/interno).
     * <p>
     * A diferencia de {@link #inviteCollaborator}, este método no necesariamente sigue
     * el flujo de invitación y puede crear colaboradores ya aceptados si se requiere.
     * </p>
     *
     * @param collaborator La entidad a persistir.
     * @return El colaborador guardado.
     */
    public Collaborator createCollaborator(Collaborator collaborator) {
        return collaboratorRepository.save(collaborator);
    }

    /**
     * Crea múltiples registros de colaboradores en una sola transacción (Batch).
     * <p>
     * Útil durante la importación de catálogos o duplicación de créditos entre canciones.
     * </p>
     *
     * @param collaborators Lista de entidades a guardar.
     * @return Lista de colaboradores persistidos.
     */
    @Transactional
    public List<Collaborator> createCollaborators(List<Collaborator> collaborators) {
        return collaboratorRepository.saveAll(collaborators);
    }

    /**
     * Actualiza la información básica de un colaborador (Rol, Instrumento, etc.).
     * <p>
     * No permite modificar campos sensibles como el estado o el porcentaje de regalías,
     * que tienen sus propios métodos con validaciones específicas.
     * </p>
     *
     * @param id ID de la colaboración a modificar.
     * @param updatedData Objeto con los nuevos datos.
     * @return La colaboración actualizada.
     * @throws RuntimeException Si la colaboración no existe.
     */
    public Collaborator updateCollaborator(Long id, Collaborator collaboratorDetails) {
        Collaborator collaborator = collaboratorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collaborator not found with id: " + id));

        collaborator.setSongId(collaboratorDetails.getSongId());
        collaborator.setArtistId(collaboratorDetails.getArtistId());
        collaborator.setRole(collaboratorDetails.getRole());

        return collaboratorRepository.save(collaborator);
    }

    /**
     * Elimina un registro de colaboración específico.
     * <p>
     * Esto revoca el acceso a créditos y regalías para ese usuario en esa obra.
     * </p>
     *
     * @param id ID de la colaboración a borrar.
     */
    public void deleteCollaborator(Long id) {
        collaboratorRepository.deleteById(id);
    }

    /**
     * Elimina masivamente todos los colaboradores asociados a una canción.
     * <p>
     * Se debe invocar cuando se elimina una canción del sistema para mantener la integridad referencial.
     * </p>
     *
     * @param songId ID de la canción.
     */
    @Transactional
    public void deleteCollaboratorsBySongId(Long songId) {
        collaboratorRepository.deleteBySongId(songId);
    }

    /**
     * Inicia el proceso de colaboración enviando una invitación.
     * <p>
     * 1. Valida que se especifique una canción O un álbum (XOR lógico).<br>
     * 2. Crea la entidad con estado {@code PENDING} y porcentaje 0.<br>
     * 3. (Opcional) Debería disparar una notificación al usuario invitado.
     * </p>
     *
     * @param request DTO con los datos de la invitación (email/ID artista, rol, obra).
     * @return La colaboración creada en estado pendiente.
     * @throws IllegalArgumentException Si falta el ID de la obra o del artista.
     */
    @Transactional
    public Collaborator inviteCollaborator(CollaborationRequest request, Long inviterId) {
        if (request.getSongId() == null && request.getAlbumId() == null) {
            throw new IllegalArgumentException("Either songId or albumId must be provided");
        }
        if (request.getSongId() != null && request.getAlbumId() != null) {
            throw new IllegalArgumentException("Cannot specify both songId and albumId");
        }

        List<Collaborator> existing;
        if (request.getSongId() != null) {
            existing = collaboratorRepository.findBySongId(request.getSongId());
        } else {
            existing = collaboratorRepository.findByAlbumId(request.getAlbumId());
        }

        boolean alreadyExists = existing.stream()
                .anyMatch(c -> c.getArtistId().equals(request.getArtistId()));

        if (alreadyExists) {
            throw new IllegalArgumentException("Collaboration already exists for this artist");
        }

        Collaborator collaborator = Collaborator.builder()
                .songId(request.getSongId())
                .albumId(request.getAlbumId())
                .artistId(request.getArtistId())
                .role(request.getRole())
                .status(CollaborationStatus.PENDING)
                .invitedBy(inviterId)
                .build();

        Collaborator saved = collaboratorRepository.save(collaborator);

        logger.info("Collaboration invitation created: {} invited artist {} for {} {}",
                inviterId, request.getArtistId(),
                saved.isForSong() ? SONG_KEY : ALBUM_KEY,
                saved.getEntityId());

        return saved;
    }
    
    /**
     * Acepta una invitación de colaboración.
     * <p>
     * Cambia el estado a {@code ACCEPTED}, haciendo que el colaborador sea visible
     * en los créditos públicos y elegible para recibir regalías.
     * </p>
     *
     * @param id ID de la colaboración.
     * @return Colaboración actualizada.
     */
    @Transactional
    public Collaborator acceptCollaboration(Long collaborationId, Long artistId) {
        Collaborator collaborator = collaboratorRepository.findById(collaborationId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + collaborationId));

        if (!collaborator.getArtistId().equals(artistId)) {
            throw new IllegalArgumentException("You are not authorized to accept this collaboration");
        }

        if (collaborator.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalArgumentException("Collaboration is not in pending status");
        }

        collaborator.setStatus(CollaborationStatus.ACCEPTED);
        Collaborator saved = collaboratorRepository.save(collaborator);

        logger.info("Collaboration accepted: artist {} accepted collaboration {} for {} {}",
                artistId, collaborationId,
                saved.isForSong() ? SONG_KEY : ALBUM_KEY,
                saved.getEntityId());

        return saved;
    }

    /**
     * Rechaza una invitación de colaboración.
     * <p>
     * Cambia el estado a {@code REJECTED}. El registro se mantiene por historial,
     * pero el porcentaje de regalías debe asegurarse en 0%.
     * </p>
     *
     * @param id ID de la colaboración.
     * @return Colaboración actualizada.
     */
    @Transactional
    public Collaborator rejectCollaboration(Long collaborationId, Long artistId) {
        Collaborator collaborator = collaboratorRepository.findById(collaborationId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + collaborationId));

        if (!collaborator.getArtistId().equals(artistId)) {
            throw new IllegalArgumentException("You are not authorized to reject this collaboration");
        }

        if (collaborator.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalArgumentException("Collaboration is not in pending status");
        }

        collaborator.setStatus(CollaborationStatus.REJECTED);
        Collaborator saved = collaboratorRepository.save(collaborator);

        logger.info("Collaboration rejected: artist {} rejected collaboration {} for {} {}",
                artistId, collaborationId,
                saved.isForSong() ? SONG_KEY : ALBUM_KEY,
                saved.getEntityId());

        return saved;
    }

    /**
     * Recupera las invitaciones que un usuario tiene pendientes de respuesta.
     * <p>
     * Fundamental para el dashboard del artista, donde ve las solicitudes entrantes.
     * </p>
     *
     * @param userId ID del usuario (artista invitado).
     * @return Lista de colaboraciones donde {@code artistId == userId} y {@code status == PENDING}.
     */
    public List<Collaborator> getPendingInvitations(Long artistId) {
        return collaboratorRepository.findByArtistIdAndStatus(artistId, CollaborationStatus.PENDING);
    }

    /**
     * Obtiene todos los colaboradores asociados a un álbum, independientemente de su estado.
     *
     * @param albumId ID del álbum.
     * @return Lista de colaboradores del proyecto.
     */
    public List<Collaborator> getCollaboratorsByAlbumId(Long albumId) {
        return collaboratorRepository.findByAlbumId(albumId);
    }

    /**
     * Obtiene únicamente los colaboradores activos (Aceptados) de una canción.
     * <p>
     * Utilizado para calcular la hoja de reparto de regalías (Split Sheet) y mostrar los créditos públicos.
     * </p>
     *
     * @param songId ID de la canción.
     * @return Lista de colaboradores con estado {@code ACCEPTED}.
     */
    public List<Collaborator> getAcceptedCollaboratorsBySongId(Long songId) {
        return collaboratorRepository.findBySongIdAndStatus(songId, CollaborationStatus.ACCEPTED);
    }

    /**
     * Obtiene únicamente los colaboradores activos (Aceptados) de un álbum.
     *
     * @param albumId ID del álbum.
     * @return Lista de colaboradores con estado {@code ACCEPTED}.
     */
    public List<Collaborator> getAcceptedCollaboratorsByAlbumId(Long albumId) {
        return collaboratorRepository.findByAlbumIdAndStatus(albumId, CollaborationStatus.ACCEPTED);
    }

    /**
     * Lista las colaboraciones iniciadas por un usuario específico.
     * <p>
     * Permite al dueño del contenido ver a quién ha invitado y el estado de esas invitaciones.
     * </p>
     *
     * @param inviterId ID del usuario que envió las invitaciones.
     * @return Lista de colaboraciones gestionadas por este usuario.
     */
    public List<Collaborator> getCollaborationsByInviter(Long inviterId) {
        return collaboratorRepository.findByInvitedBy(inviterId);
    }

    /**
     * Elimina masivamente todos los colaboradores asociados a un álbum.
     * <p>
     * Se debe invocar al eliminar un álbum.
     * </p>
     *
     * @param albumId ID del álbum.
     */
    @Transactional
    public void deleteCollaboratorsByAlbumId(Long albumId) {
        collaboratorRepository.deleteByAlbumId(albumId);
    }

    /**
     * Actualiza el porcentaje de regalías de un colaborador.
     * <p>
     * <b>Validación Crítica:</b> Verifica que la suma del nuevo porcentaje más los porcentajes
     * de los otros colaboradores existentes no exceda el 100.00%.
     * </p>
     *
     * @param id ID de la colaboración.
     * @param request Nuevo porcentaje solicitado.
     * @param userId ID del usuario que solicita el cambio (seguridad).
     * @return Colaboración actualizada.
     * @throws IllegalArgumentException Si la suma total supera el 100%.
     */
    @Transactional
    public Collaborator updateRevenuePercentage(Long collaborationId, UpdateRevenueRequest request, Long userId) {
        Collaborator collaborator = collaboratorRepository.findById(collaborationId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + collaborationId));

        if (!collaborator.getInvitedBy().equals(userId)) {
            throw new IllegalArgumentException("Only the creator can update revenue percentage");
        }

        if (collaborator.getStatus() != CollaborationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Can only set revenue percentage for accepted collaborations");
        }

        BigDecimal currentTotal = calculateTotalRevenuePercentage(
                collaborator.getSongId(),
                collaborator.getAlbumId(),
                collaborationId
        );

        BigDecimal newTotal = currentTotal.add(request.getRevenuePercentage());
        if (newTotal.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    String.format("Total revenue percentage would exceed 100%%. Current: %.2f%%, Requested: %.2f%%, Total would be: %.2f%%",
                            currentTotal, request.getRevenuePercentage(), newTotal)
            );
        }

        collaborator.setRevenuePercentage(request.getRevenuePercentage());
        Collaborator saved = collaboratorRepository.save(collaborator);

        logger.info("Revenue percentage updated: collaboration {} set to {}%",
                collaborationId, request.getRevenuePercentage());

        return saved;
    }

    /**
     * Calcula la suma total de porcentajes asignados en una obra.
     * <p>
     * Se utiliza internamente para validaciones y externamente para mostrar
     * al usuario cuánto "pastel" queda disponible para repartir.
     * </p>
     *
     * @param songId ID de la canción (opcional).
     * @param albumId ID del álbum (opcional).
     * @param excludeCollaborationId ID a excluir de la suma (útil durante actualizaciones).
     * @return Suma total como BigDecimal.
     */
    private BigDecimal calculateTotalRevenuePercentage(Long songId, Long albumId, Long excludeCollaborationId) {
        List<Collaborator> collaborators;

        if (songId != null) {
            collaborators = collaboratorRepository.findBySongIdAndStatus(songId, CollaborationStatus.ACCEPTED);
        } else if (albumId != null) {
            collaborators = collaboratorRepository.findByAlbumIdAndStatus(albumId, CollaborationStatus.ACCEPTED);
        } else {
            return BigDecimal.ZERO;
        }

        return collaborators.stream()
                .filter(c -> !c.getId().equals(excludeCollaborationId))
                .map(Collaborator::getRevenuePercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula el porcentaje total de regalías asignado actualmente a una canción.
     *
     * @param songId ID de la canción.
     * @return Suma de porcentajes (BigDecimal).
     */
    public BigDecimal getTotalRevenuePercentageForSong(Long songId) {
        return calculateTotalRevenuePercentage(songId, null, null);
    }

    /**
     * Calcula el porcentaje total de regalías asignado actualmente a un álbum.
     *
     * @param albumId ID del álbum.
     * @return Suma de porcentajes (BigDecimal).
     */
    public BigDecimal getTotalRevenuePercentageForAlbum(Long albumId) {
        return calculateTotalRevenuePercentage(null, albumId, null);
    }
}