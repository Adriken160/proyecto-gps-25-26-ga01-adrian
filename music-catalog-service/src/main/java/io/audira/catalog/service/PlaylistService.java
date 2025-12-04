package io.audira.catalog.service;

import io.audira.catalog.model.Playlist;
import io.audira.catalog.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio para la gestión de listas de reproducción (Playlists).
 * <p>
 * Encapsula la lógica de negocio para crear, modificar, eliminar y organizar
 * listas de reproducción de usuarios. Implementa los requisitos funcionales:
 * <ul>
 * <li><b>GA01-113:</b> Crear lista con nombre y descripción.</li>
 * <li><b>GA01-114:</b> Añadir y eliminar canciones de las listas.</li>
 * <li><b>GA01-115:</b> Editar nombre, visibilidad y eliminar listas completas.</li>
 * </ul>
 * </p>
 */
@RequiredArgsConstructor
@Service
public class PlaylistService {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistService.class);
    private static final String WARNING_KEY = "Playlist not found with id: ";

    private final PlaylistRepository playlistRepository;

    /**
     * Recupera todas las listas de reproducción existentes en el sistema.
     * <p>
     * Utilizado principalmente para propósitos administrativos o de depuración.
     * </p>
     *
     * @return Una lista completa de todas las entidades {@link Playlist}.
     */
    public List<Playlist> getAllPlaylists() {
        return playlistRepository.findAll();
    }

    /**
     * Busca una lista de reproducción por su identificador único.
     * <p>
     * Soporta el requisito <b>GA01-113: Ver detalles de playlist</b>.
     * </p>
     *
     * @param id El ID de la playlist a buscar.
     * @return Un {@link Optional} que contiene la playlist si se encuentra.
     */
    public Optional<Playlist> getPlaylistById(Long id) {
        return playlistRepository.findById(id);
    }

    /**
     * Get all playlists created by a user
     * GA01-113: List user's playlists
     * @param userId User ID
     * @return List of user's playlists
     */
    public List<Playlist> getUserPlaylists(Long userId) {
        return playlistRepository.findByUserId(userId);
    }

    /**
     * Recupera todas las listas de reproducción marcadas como públicas en el sistema.
     * <p>
     * Estas listas son visibles para cualquier usuario en la sección de comunidad o descubrimiento.
     * </p>
     *
     * @return Lista de playlists con {@code isPublic = true}.
     */
    public List<Playlist> getPublicPlaylists() {
        return playlistRepository.findByIsPublicTrue();
    }

    /**
     * Crea una nueva lista de reproducción.
     * <p>
     * Valida que el nombre no esté vacío antes de guardar.
     * </p>
     *
     * @param playlist La entidad {@link Playlist} con los datos iniciales.
     * @return La playlist creada y persistida.
     * @throws IllegalArgumentException Si el nombre de la playlist es nulo o está vacío.
     */
    @Transactional
    public Playlist createPlaylist(Playlist playlist) {
        if (playlist.getName() == null || playlist.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Playlist name is required");
        }
        if (playlist.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        logger.info("Creating playlist '{}' for user {}", playlist.getName(), playlist.getUserId());
        return playlistRepository.save(playlist);
    }

    /**
     * Actualiza los metadatos de una lista de reproducción existente.
     * <p>
     * Permite modificar el nombre, la descripción y la visibilidad (pública/privada).
     * </p>
     *
     * @param id El ID de la playlist a modificar.
     * @param playlistDetails Objeto con los nuevos valores a aplicar.
     * @return La playlist actualizada.
     * @throws RuntimeException Si la playlist no existe.
     */
    @Transactional
    public Playlist updatePlaylist(Long id, String name, String description, Boolean isPublic) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + id));

        if (name != null && !name.trim().isEmpty()) {
            playlist.setName(name.trim());
        }
        if (description != null) {
            playlist.setDescription(description.trim().isEmpty() ? null : description.trim());
        }
        if (isPublic != null) {
            playlist.setIsPublic(isPublic);
        }

        logger.info("Updating playlist {} with new details", id);
        return playlistRepository.save(playlist);
    }

    /**
     * Elimina una lista de reproducción del sistema.
     * <p>
     * <b>GA01-115:</b> Borrado permanente de una lista.
     * </p>
     *
     * @param id El ID de la playlist a eliminar.
     */
    @Transactional
    public void deletePlaylist(Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + id));

        logger.info("Deleting playlist {} '{}' for user {}", id, playlist.getName(), playlist.getUserId());
        playlistRepository.deleteById(id);
    }

    /**
     * Añade una canción a una lista de reproducción.
     * <p>
     * <b>GA01-114:</b> Verifica primero si la playlist existe y delega la lógica de adición
     * (y control de duplicados) a la entidad {@link Playlist}.
     * </p>
     *
     * @param playlistId El ID de la playlist destino.
     * @param songId El ID de la canción a añadir.
     * @return La playlist actualizada con la nueva canción.
     * @throws RuntimeException Si la playlist no se encuentra.
     */
    @Transactional
    public Playlist addSongToPlaylist(Long playlistId, Long songId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + playlistId));

        if (playlist.containsSong(songId)) {
            logger.warn("Song {} already exists in playlist {}", songId, playlistId);
            return playlist; // Song already in playlist
        }

        playlist.addSong(songId);
        logger.info("Adding song {} to playlist {}", songId, playlistId);
        return playlistRepository.save(playlist);
    }

    /**
     * Elimina una canción específica de una lista de reproducción.
     * <p>
     * <b>GA01-114:</b> Elimina la referencia a la canción manteniendo el resto de la lista intacta.
     * </p>
     *
     * @param playlistId El ID de la playlist.
     * @param songId El ID de la canción a remover.
     * @return La playlist actualizada.
     * @throws RuntimeException Si la playlist no se encuentra.
     */
    @Transactional
    public Playlist removeSongFromPlaylist(Long playlistId, Long songId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + playlistId));

        boolean removed = playlist.removeSong(songId);
        if (removed) {
            logger.info("Removing song {} from playlist {}", songId, playlistId);
            return playlistRepository.save(playlist);
        } else {
            logger.warn("Song {} not found in playlist {}", songId, playlistId);
            return playlist;
        }
    }

    /**
     * Reordena las canciones dentro de una lista de reproducción.
     * <p>
     * Recibe una nueva lista de IDs que representa el orden deseado.
     * Realiza validaciones estrictas para asegurar la integridad de datos:
     * <ol>
     * <li>Todas las canciones en la nueva lista deben pertenecer a la playlist original.</li>
     * <li>La cantidad de canciones debe coincidir (no se puede añadir/borrar canciones en este método).</li>
     * </ol>
     * </p>
     *
     * @param playlistId El ID de la playlist a reordenar.
     * @param songIds La lista de IDs de canciones en el nuevo orden.
     * @return La playlist con el orden actualizado.
     * @throws IllegalArgumentException Si la lista proporcionada no coincide en contenido con la playlist actual.
     * @throws RuntimeException Si la playlist no existe.
     */
    @Transactional
    public Playlist reorderPlaylistSongs(Long playlistId, List<Long> songIds) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException(WARNING_KEY + playlistId));

        for (Long songId : songIds) {
            if (!playlist.containsSong(songId)) {
                throw new IllegalArgumentException("Song " + songId + " is not in the playlist");
            }
        }

        if (songIds.size() != playlist.getSongCount()) {
            throw new IllegalArgumentException("Song count mismatch. Expected " +
                    playlist.getSongCount() + " songs, but got " + songIds.size());
        }

        playlist.setSongIds(songIds);
        logger.info("Reordering songs in playlist {}", playlistId);
        return playlistRepository.save(playlist);
    }

    /**
     * Verifica si un usuario es el propietario legítimo de una playlist.
     * <p>
     * Utilizado en los controladores para validar permisos de edición o borrado.
     * </p>
     *
     * @param playlistId El ID de la playlist.
     * @param userId El ID del usuario a verificar.
     * @return {@code true} si el usuario es el dueño, {@code false} en caso contrario.
     */
    public boolean isPlaylistOwner(Long playlistId, Long userId) {
        Optional<Playlist> playlist = playlistRepository.findById(playlistId);
        return playlist.isPresent() && playlist.get().getUserId().equals(userId);
    }

    /**
     * Busca en qué listas de reproducción de un usuario aparece una canción específica.
     * <p>
     * Permite al usuario saber rápidamente si ya ha guardado una canción y en qué listas.
     * </p>
     *
     * @param userId El ID del usuario.
     * @param songId El ID de la canción.
     * @return Lista de playlists del usuario que contienen la canción dada.
     */
    public List<Playlist> getPlaylistsContainingSong(Long userId, Long songId) {
        return playlistRepository.findByUserId(userId).stream()
                .filter(playlist -> playlist.containsSong(songId))
                .toList();
    }
}
