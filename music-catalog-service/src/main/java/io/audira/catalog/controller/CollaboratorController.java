package io.audira.catalog.controller;

import io.audira.catalog.model.Collaborator;
import io.audira.catalog.service.CollaboratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import io.audira.catalog.dto.CollaborationRequest;
import io.audira.catalog.dto.UpdateRevenueRequest;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión de colaboradores y reparto de regalías (Splits).
 * <p>
 * Implementa la lógica para invitar artistas a colaborar en canciones o álbumes,
 * gestionar la aceptación de dichas invitaciones y definir los porcentajes de ganancias.
 * Cubre los requisitos GA01-154 (Gestión de colaboradores) y GA01-155 (Porcentajes).
 * </p>
 */
@RestController
@RequestMapping("/api/collaborators")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CollaboratorController {

    private final CollaboratorService collaboratorService;

    /**
     * Lista todos los colaboradores del sistema.
     * @return Lista de colaboradores.
     */
    @GetMapping
    public ResponseEntity<List<Collaborator>> getAllCollaborators() {
        return ResponseEntity.ok(collaboratorService.getAllCollaborators());
    }

    /**
     * Obtiene un registro de colaboración por ID.
     * @param id ID de la colaboración.
     * @return Detalle de la colaboración.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Collaborator> getCollaboratorById(@PathVariable Long id) {
        return collaboratorService.getCollaboratorById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista los colaboradores asociados a una canción específica.
     * @param songId ID de la canción.
     * @return Lista de colaboradores.
     */
    @GetMapping("/song/{songId}")
    public ResponseEntity<List<Collaborator>> getCollaboratorsBySongId(@PathVariable Long songId) {
        return ResponseEntity.ok(collaboratorService.getCollaboratorsBySongId(songId));
    }

    /**
     * Lista los colaboradores asociados a un artista específico.
     * @param artistId ID del artista.
     * @return Lista de colaboradores.
     */
    @GetMapping("/artist/{artistId}")
    public ResponseEntity<List<Collaborator>> getCollaboratorsByArtistId(@PathVariable Long artistId) {
        return ResponseEntity.ok(collaboratorService.getCollaboratorsByArtistId(artistId));
    }

    /**
     * Crea un registro de colaborador manualmente (uso interno o administrativo).
     * <p>
     * A diferencia de {@code inviteCollaborator}, este método puede no enviar notificaciones
     * y forzar el estado de la colaboración.
     * </p>
     *
     * @param collaborator Objeto con los datos del colaborador.
     * @return El colaborador creado.
     */
    @PostMapping
    public ResponseEntity<Collaborator> createCollaborator(@RequestBody Collaborator collaborator) {
        Collaborator created = collaboratorService.createCollaborator(collaborator);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Crea múltiples colaboradores en lote.
     * <p>
     * Útil al importar catálogos o duplicar créditos de una canción a otra.
     * </p>
     *
     * @param collaborators Lista de objetos colaborador.
     * @return Lista de colaboradores creados.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<Collaborator>> createCollaborators(@RequestBody List<Collaborator> collaborators) {
        List<Collaborator> created = collaboratorService.createCollaborators(collaborators);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Actualiza los datos generales de un colaborador.
     * <p>
     * Permite modificar rol, instrumento o metadatos sin cambiar el estado o el porcentaje (que tienen endpoints propios).
     * </p>
     *
     * @param id ID de la colaboración.
     * @param collaborator Datos a actualizar.
     * @return Colaborador actualizado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Collaborator> updateCollaborator(
            @PathVariable Long id,
            @RequestBody Collaborator collaboratorDetails) {
        try {
            Collaborator updated = collaboratorService.updateCollaborator(id, collaboratorDetails);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Elimina un colaborador específico.
     * <p>
     * Revoca el acceso y elimina el registro de participación.
     * </p>
     *
     * @param id ID de la colaboración a eliminar.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollaborator(@PathVariable Long id) {
        collaboratorService.deleteCollaborator(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Elimina todos los colaboradores asociados a una canción.
     * <p>
     * Se utiliza típicamente antes de una actualización masiva de créditos o al borrar la canción.
     * </p>
     *
     * @param songId ID de la canción.
     * @return 204 No Content.
     */
    @DeleteMapping("/song/{songId}")
    public ResponseEntity<Void> deleteCollaboratorsBySongId(@PathVariable Long songId) {
        collaboratorService.deleteCollaboratorsBySongId(songId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Envía una invitación formal de colaboración.
     * <p>
     * Inicia el flujo de aprobación: el registro se crea con estado {@code PENDING} y se notifica al usuario.
     * </p>
     *
     * @param request Datos de la invitación (email, rol, porcentaje).
     * @return La colaboración en estado pendiente.
     */
    @PostMapping("/invite")
    public ResponseEntity<Collaborator> inviteCollaborator(
            @Valid @RequestBody CollaborationRequest request,
            @RequestParam Long inviterId) {
        try {
            Collaborator collaboration = collaboratorService.inviteCollaborator(request, inviterId);
            return ResponseEntity.status(HttpStatus.CREATED).body(collaboration);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Acepta una invitación de colaboración.
     * <p>
     * Cambia el estado a {@code ACCEPTED}. Debe ser invocado por el usuario invitado.
     * </p>
     *
     * @param id ID de la colaboración.
     * @return Colaboración aceptada.
     */
    @PutMapping("/{id}/accept")
    public ResponseEntity<Collaborator> acceptCollaboration(
            @PathVariable Long id,
            @RequestParam Long artistId) {
        try {
            Collaborator collaboration = collaboratorService.acceptCollaboration(id, artistId);
            return ResponseEntity.ok(collaboration);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Rechaza una invitación de colaboración.
     * <p>
     * Cambia el estado a {@code REJECTED} y libera el porcentaje de regalías reservado.
     * </p>
     *
     * @param id ID de la colaboración.
     * @return Colaboración rechazada.
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<Collaborator> rejectCollaboration(
            @PathVariable Long id,
            @RequestParam Long artistId) {
        try {
            Collaborator collaboration = collaboratorService.rejectCollaboration(id, artistId);
            return ResponseEntity.ok(collaboration);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Obtiene las invitaciones pendientes para un usuario.
     * <p>
     * Permite al usuario ver en su dashboard qué artistas le han invitado a colaborar.
     * </p>
     *
     * @param userId ID del usuario consultado.
     * @return Lista de colaboraciones en estado PENDING donde el usuario es el invitado.
     */
    @GetMapping("/pending/{artistId}")
    public ResponseEntity<List<Collaborator>> getPendingInvitations(@PathVariable Long artistId) {
        List<Collaborator> invitations = collaboratorService.getPendingInvitations(artistId);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Lista los colaboradores de un álbum por su ID.
     * <p>
     * Alias directo para consultas por ID de álbum.
     * </p>
     *
     * @param albumId ID del álbum.
     * @return Lista de colaboradores.
     */
    @GetMapping("/album/{albumId}")
    public ResponseEntity<List<Collaborator>> getCollaboratorsByAlbumId(@PathVariable Long albumId) {
        List<Collaborator> collaborators = collaboratorService.getCollaboratorsByAlbumId(albumId);
        return ResponseEntity.ok(collaborators);
    }

    /**
     * Obtiene solo los colaboradores confirmados (ACCEPTED) de una canción.
     * <p>
     * Fundamental para el cálculo final de regalías (Split Sheets) y visualización pública de créditos.
     * </p>
     *
     * @param songId ID de la canción.
     * @return Lista de colaboradores activos.
     */
    @GetMapping("/song/{songId}/accepted")
    public ResponseEntity<List<Collaborator>> getAcceptedCollaboratorsBySongId(@PathVariable Long songId) {
        List<Collaborator> collaborators = collaboratorService.getAcceptedCollaboratorsBySongId(songId);
        return ResponseEntity.ok(collaborators);
    }

    /**
     * Obtiene solo los colaboradores confirmados (ACCEPTED) de un álbum.
     *
     * @param albumId ID del álbum.
     * @return Lista de colaboradores activos del álbum.
     */
    @GetMapping("/album/{albumId}/accepted")
    public ResponseEntity<List<Collaborator>> getAcceptedCollaboratorsByAlbumId(@PathVariable Long albumId) {
        List<Collaborator> collaborators = collaboratorService.getAcceptedCollaboratorsByAlbumId(albumId);
        return ResponseEntity.ok(collaborators);
    }

    /**
     * Obtiene las colaboraciones iniciadas por un usuario específico.
     * <p>
     * Muestra a quién ha invitado este usuario a participar en sus obras.
     * </p>
     *
     * @param inviterId ID del usuario que envió las invitaciones (dueño del contenido).
     * @return Lista de colaboraciones gestionadas por este usuario.
     */
    @GetMapping("/inviter/{inviterId}")
    public ResponseEntity<List<Collaborator>> getCollaborationsByInviter(@PathVariable Long inviterId) {
        List<Collaborator> collaborations = collaboratorService.getCollaborationsByInviter(inviterId);
        return ResponseEntity.ok(collaborations);
    }

    /**
     * Elimina todos los colaboradores asociados a un álbum.
     * <p>
     * Se utiliza típicamente antes de una actualización masiva de créditos o al borrar el álbum.
     * </p>
     *
     * @param albumId ID del álbum.
     * @return 204 No Content.
     */
    @DeleteMapping("/album/{albumId}")
    public ResponseEntity<Void> deleteCollaboratorsByAlbumId(@PathVariable Long albumId) {
        collaboratorService.deleteCollaboratorsByAlbumId(albumId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Modifica el porcentaje de regalías asignado a un colaborador.
     * @param id ID de la colaboración.
     * @param request Nuevo porcentaje.
     * @param userId ID del usuario que solicita el cambio (seguridad).
     * @return Colaboración actualizada.
     */
    @PutMapping("/{id}/revenue")
    public ResponseEntity<Collaborator> updateRevenuePercentage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRevenueRequest request,
            @RequestParam Long userId) {
        try {
            Collaborator collaboration = collaboratorService.updateRevenuePercentage(id, request, userId);
            return ResponseEntity.ok(collaboration);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Calcula el total de porcentaje de regalías asignado en una canción.
     * <p>Útil para validar que no supere el 100%.</p>
     * @param songId ID de la canción.
     * @return Mapa con el totalPercentage.
     */
    @GetMapping("/song/{songId}/revenue-total")
    public ResponseEntity<Map<String, BigDecimal>> getTotalRevenuePercentageForSong(@PathVariable Long songId) {
        BigDecimal total = collaboratorService.getTotalRevenuePercentageForSong(songId);
        return ResponseEntity.ok(Map.of("totalPercentage", total));
    }

    /**
     * Calcula el total de porcentaje de regalías asignado en un álbum.
     * @param albumId ID del álbum.
     * @return Mapa con el totalPercentage.
     */
    @GetMapping("/album/{albumId}/revenue-total")
    public ResponseEntity<Map<String, BigDecimal>> getTotalRevenuePercentageForAlbum(@PathVariable Long albumId) {
        BigDecimal total = collaboratorService.getTotalRevenuePercentageForAlbum(albumId);
        return ResponseEntity.ok(Map.of("totalPercentage", total));
    }
}