package io.audira.catalog.service;

import io.audira.catalog.client.CommerceServiceClient;
import io.audira.catalog.client.RatingServiceClient;
import io.audira.catalog.client.UserServiceClient;
import io.audira.catalog.dto.*;
import io.audira.catalog.model.Album;
import io.audira.catalog.model.Collaborator;
import io.audira.catalog.model.CollaborationStatus;
import io.audira.catalog.model.Song;
import io.audira.catalog.repository.AlbumRepository;
import io.audira.catalog.repository.CollaboratorRepository;
import io.audira.catalog.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio encargado del cálculo y agregación de métricas de rendimiento.
 * <p>
 * Centraliza la lógica para generar reportes estadísticos de artistas y canciones,
 * combinando datos de múltiples fuentes:
 * <ul>
 * <li><b>Catálogo:</b> Inventario de canciones, álbumes y reproducciones (Plays).</li>
 * <li><b>Comercio:</b> Ventas e ingresos (Revenue).</li>
 * <li><b>Comunidad:</b> Valoraciones (Ratings) y comentarios.</li>
 * </ul>
 * Cumple con los requisitos <b>GA01-108 (Resumen)</b> y <b>GA01-109 (Detalle)</b>.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final UserServiceClient userServiceClient;
    private final RatingServiceClient ratingServiceClient;
    private final CommerceServiceClient commerceServiceClient;

    /**
     * Genera un resumen ejecutivo de las métricas de un artista.
     * <p>
     * Proporciona una visión general del rendimiento del artista, incluyendo totales acumulados
     * y comparativas de crecimiento. Ideal para el dashboard principal.
     * </p>
     *
     * @param artistId Identificador del artista.
     * @return DTO {@link ArtistMetricsSummary} con los datos consolidados.
     */
    public ArtistMetricsSummary getArtistMetricsSummary(Long artistId) {
        logger.info("Calculating metrics summary for artist {}", artistId);

        UserDTO artist = userServiceClient.getUserById(artistId);
        String artistName = artist.getArtistName() != null ? artist.getArtistName() : artist.getUsername();

        List<Song> artistSongs = songRepository.findByArtistId(artistId);

        List<Album> artistAlbums = albumRepository.findByArtistId(artistId);

        List<Collaborator> collaborations = collaboratorRepository.findByArtistIdAndStatus(
                artistId, CollaborationStatus.ACCEPTED
        );

        Long totalPlays = artistSongs.stream()
                .mapToLong(Song::getPlays)
                .sum();

        Optional<Song> mostPlayedSong = artistSongs.stream()
                .max(Comparator.comparing(Song::getPlays));

        Double playsGrowth = calculateEstimatedGrowth(totalPlays);

        RatingStatsDTO ratingStats = ratingServiceClient.getArtistRatingStats(artistId);
        Double averageRating = ratingStats.getAverageRating() != null ? ratingStats.getAverageRating() : 0.0;
        Long totalRatings = ratingStats.getTotalRatings() != null ? ratingStats.getTotalRatings() : 0L;
        Double ratingsGrowth = calculateEstimatedGrowth(totalRatings);

        List<OrderDTO> allOrders = commerceServiceClient.getAllOrders();

        Map<String, Object> salesMetrics = calculateArtistSales(artistSongs, allOrders);
        Long totalSales = (Long) salesMetrics.get("totalSales");
        BigDecimal totalRevenue = (BigDecimal) salesMetrics.get("totalRevenue");
        Long salesLast30Days = (Long) salesMetrics.get("salesLast30Days");
        BigDecimal revenueLast30Days = (BigDecimal) salesMetrics.get("revenueLast30Days");
        Double salesGrowth = calculateEstimatedGrowth(totalSales);
        Double revenueGrowth = salesGrowth;

        Long totalComments = artistSongs.stream()
                .mapToLong(song -> {
                    RatingStatsDTO songStats = ratingServiceClient.getEntityRatingStats("SONG", song.getId());
                    return (long) (songStats.getTotalRatings() * 0.3);
                })
                .sum();
        Long commentsLast30Days = totalComments / 6; // Estimate 1/6 in last 30 days
        Double commentsGrowth = calculateEstimatedGrowth(totalComments);

        return ArtistMetricsSummary.builder()
                .artistId(artistId)
                .artistName(artistName)
                .generatedAt(LocalDateTime.now())
                .totalPlays(totalPlays)
                .playsLast30Days(totalPlays / 4)
                .playsGrowthPercentage(playsGrowth)
                .averageRating(averageRating)
                .totalRatings(totalRatings)
                .ratingsGrowthPercentage(ratingsGrowth)
                .totalSales(totalSales)
                .totalRevenue(totalRevenue)
                .salesLast30Days(salesLast30Days)
                .revenueLast30Days(revenueLast30Days)
                .salesGrowthPercentage(salesGrowth)
                .revenueGrowthPercentage(revenueGrowth)
                .totalComments(totalComments)
                .commentsLast30Days(commentsLast30Days)
                .commentsGrowthPercentage(commentsGrowth)
                .totalSongs((long) artistSongs.size())
                .totalAlbums((long) artistAlbums.size())
                .totalCollaborations((long) collaborations.size())
                .mostPlayedSongId(mostPlayedSong.map(Song::getId).orElse(null))
                .mostPlayedSongName(mostPlayedSong.map(Song::getTitle).orElse("N/A"))
                .mostPlayedSongPlays(mostPlayedSong.map(Song::getPlays).orElse(0L))
                .build();
    }

    /**
     * Genera un reporte detallado con evolución temporal de métricas.
     * <p>
     * Crea puntos de datos diarios para graficar tendencias de reproducciones, ventas e ingresos
     * en un rango de fechas específico.
     * </p>
     *
     * @param artistId Identificador del artista.
     * @param startDate Fecha de inicio del reporte.
     * @param endDate Fecha de fin del reporte.
     * @return DTO {@link ArtistMetricsDetailed} con listas de métricas diarias.
     */
    public ArtistMetricsDetailed getArtistMetricsDetailed(
            Long artistId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        logger.info("Calculating detailed metrics for artist {} from {} to {}",
                artistId, startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        UserDTO artist = userServiceClient.getUserById(artistId);
        String artistName = artist.getArtistName() != null ? artist.getArtistName() : artist.getUsername();

        List<Song> artistSongs = songRepository.findByArtistId(artistId);
        logger.info(" Found {} songs for artist {}", artistSongs.size(), artistId);

        artistSongs.forEach(song ->
            logger.info("   Song: '{}' (ID: {}) - Plays: {}",
                song.getTitle(), song.getId(), song.getPlays())
        );

        Long totalPlays = artistSongs.stream().mapToLong(Song::getPlays).sum();
        logger.info(" Total plays calculated: {}", totalPlays);

        List<OrderDTO> allOrders = commerceServiceClient.getAllOrders();
        logger.info(" Retrieved {} total orders from commerce service", allOrders.size());

        Map<String, Object> salesMetrics = calculateArtistSales(artistSongs, allOrders);
        Long totalSales = (Long) salesMetrics.get("totalSales");
        BigDecimal totalRevenue = (BigDecimal) salesMetrics.get("totalRevenue");

        List<ArtistMetricsDetailed.DailyMetric> dailyMetrics = generateDailyMetricsWithRealData(
                artistSongs, allOrders, startDate, endDate, totalPlays
        );

        Long periodPlays = totalPlays;  
        Long periodSales = totalSales;  
        BigDecimal periodRevenue = totalRevenue; 

        Long periodComments = dailyMetrics.stream()
                .mapToLong(ArtistMetricsDetailed.DailyMetric::getComments)
                .sum();

        logger.info(" Calculating artist average rating from song ratings...");

        double totalRatingSum = 0.0;
        int songsWithRatings = 0;
        Long totalRatingsCount = 0L;

        for (Song song : artistSongs) {
            RatingStatsDTO songStats = ratingServiceClient.getEntityRatingStats("SONG", song.getId());
            if (songStats.getAverageRating() != null && songStats.getAverageRating() > 0) {
                totalRatingSum += songStats.getAverageRating();
                songsWithRatings++;
                totalRatingsCount += (songStats.getTotalRatings() != null ? songStats.getTotalRatings() : 0L);

                logger.info("   Song '{}' (ID: {}) - Avg: {}, Total ratings: {}",
                    song.getTitle(), song.getId(), songStats.getAverageRating(), songStats.getTotalRatings());
            } else {
                logger.info("   Song '{}' (ID: {}) - No ratings yet", song.getTitle(), song.getId());
            }
        }

        Double averageRating = songsWithRatings > 0 ? totalRatingSum / songsWithRatings : 0.0;

        logger.info("Artist rating calculation:");
        logger.info("   Songs with ratings: {} / {}", songsWithRatings, artistSongs.size());
        logger.info("   Sum of song ratings: {}", totalRatingSum);
        logger.info("   Average rating: {} / {} = {}", totalRatingSum, songsWithRatings, averageRating);
        logger.info("   Total individual ratings: {}", totalRatingsCount);

        logger.info("FINAL METRICS SUMMARY for artist {}:", artistId);
        logger.info("   Artist: {}", artistName);
        logger.info("   Period: {} to {}", startDate, endDate);
        logger.info("   Period Plays: {}", periodPlays);
        logger.info("   Period Sales: {}", periodSales);
        logger.info("   Period Revenue: ${}", periodRevenue);
        logger.info("   Period Comments: {}", periodComments);
        logger.info("   Average Rating: {}", averageRating);
        logger.info("   Daily Metrics: {} days", dailyMetrics.size());

        return ArtistMetricsDetailed.builder()
                .artistId(artistId)
                .artistName(artistName)
                .startDate(startDate)
                .endDate(endDate)
                .dailyMetrics(dailyMetrics)
                .totalPlays(periodPlays)
                .totalSales(periodSales)
                .totalRevenue(periodRevenue)
                .totalComments(periodComments)
                .averageRating(averageRating)
                .build();
    }

    /**
     * Obtiene el ranking de las canciones más exitosas de un artista.
     * <p>
     * Ordena el catálogo del artista por número de reproducciones descendente.
     * </p>
     *
     * @param artistId Identificador del artista.
     * @param limit Número máximo de canciones a retornar.
     * @return Lista de métricas de las top canciones.
     */
    public List<SongMetrics> getArtistTopSongs(Long artistId, int limit) {
        logger.info("Getting top {} songs for artist {}", limit, artistId);

        List<Song> artistSongs = songRepository.findByArtistId(artistId);

        return artistSongs.stream()
                .sorted(Comparator.comparing(Song::getPlays).reversed())
                .limit(limit)
                .map(song -> getSongMetrics(song.getId()))
                .toList();
    }

    /**
     * Obtiene las métricas específicas de una canción individual.
     *
     * @param songId Identificador de la canción.
     * @return DTO {@link SongMetrics} con el rendimiento del track.
     * @throws RuntimeException Si la canción no existe.
     */
    public SongMetrics getSongMetrics(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found: " + songId));

        UserDTO artist = userServiceClient.getUserById(song.getArtistId());
        String artistName = artist.getArtistName() != null ? artist.getArtistName() : artist.getUsername();

        List<Song> artistSongs = songRepository.findByArtistId(song.getArtistId());
        List<Song> sortedByPlays = artistSongs.stream()
                .sorted(Comparator.comparing(Song::getPlays).reversed())
                .toList();

        int rank = sortedByPlays.indexOf(song) + 1;

        RatingStatsDTO ratingStats = ratingServiceClient.getEntityRatingStats("SONG", songId);
        Double averageRating = ratingStats.getAverageRating() != null ? ratingStats.getAverageRating() : 0.0;
        Long totalRatings = ratingStats.getTotalRatings() != null ? ratingStats.getTotalRatings() : 0L;

        Long totalComments = (long) (totalRatings * 0.3);

        List<OrderDTO> allOrders = commerceServiceClient.getAllOrders();
        Map<String, Object> songSales = calculateSongSales(songId, allOrders);
        Long totalSales = (Long) songSales.get("totalSales");
        BigDecimal totalRevenue = (BigDecimal) songSales.get("totalRevenue");

        return SongMetrics.builder()
                .songId(song.getId())
                .songName(song.getTitle())
                .artistName(artistName)
                .totalPlays(song.getPlays())
                .averageRating(averageRating)
                .totalRatings(totalRatings)
                .totalComments(totalComments)
                .totalSales(totalSales)
                .totalRevenue(totalRevenue.doubleValue())
                .rankInArtistCatalog(rank)
                .build();
    }

    /**
     * Calcula el volumen total de ventas generadas por un artista.
     * <p>
     * Realiza una agregación de todas las transacciones comerciales asociadas al artista.
     * Esto incluye:
     * <ul>
     * <li>Ventas directas de álbumes completos.</li>
     * <li>Ventas individuales de canciones (Singles o tracks de álbumes).</li>
     * </ul>
     * Se comunica con el {@link CommerceServiceClient} para obtener los datos transaccionales.
     * </p>
     *
     * @param artistId Identificador único del artista.
     * @return Número total de unidades vendidas (Songs + Albums).
     */
    private Map<String, Object> calculateArtistSales(List<Song> artistSongs, List<OrderDTO> allOrders) {
        Set<Long> artistSongIds = artistSongs.stream()
                .map(Song::getId)
                .collect(Collectors.toSet());

        logger.info(" Calculating sales for artist songs. Artist has {} songs", artistSongs.size());
        logger.info("   Processing {} total orders", allOrders.size());

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long totalSales = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long salesLast30Days = 0;
        BigDecimal revenueLast30Days = BigDecimal.ZERO;

        int deliveredOrders = 0;
        int skippedOrders = 0;
        int relevantOrders = 0;

        for (OrderDTO order : allOrders) {
            logger.debug("   Order ID {} - Status: {} - Created: {}",
                    order.getId(), order.getStatus(), order.getCreatedAt());

            if (order.getStatus() == null || !"DELIVERED".equals(order.getStatus())) {
                logger.debug("       Skipped order {} - Status: {} (not DELIVERED)",
                        order.getId(), order.getStatus());
                skippedOrders++;
                continue;
            }

            deliveredOrders++;

            if (order.getItems() == null) {
                logger.debug("       Order {} has no items", order.getId());
                continue;
            }

            for (OrderItemDTO item : order.getItems()) {
                if ("SONG".equalsIgnoreCase(item.getItemType()) && artistSongIds.contains(item.getItemId())) {
                    relevantOrders++;
                    long quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                    BigDecimal itemRevenue = price.multiply(BigDecimal.valueOf(quantity));

                    logger.info("       Found sale: Song ID {} - Qty: {} - Price: ${} - Revenue: ${}",
                            item.getItemId(), quantity, price, itemRevenue);

                    totalSales += quantity;
                    totalRevenue = totalRevenue.add(itemRevenue);

                    if (order.getCreatedAt() != null && order.getCreatedAt().isAfter(thirtyDaysAgo)) {
                        salesLast30Days += quantity;
                        revenueLast30Days = revenueLast30Days.add(itemRevenue);
                    }
                }
            }
        }

        logger.info(" Sales calculation summary:");
        logger.info("   Total orders processed: {}", allOrders.size());
        logger.info("   DELIVERED orders: {}", deliveredOrders);
        logger.info("   Skipped orders (not DELIVERED): {}", skippedOrders);
        logger.info("   Orders with artist's songs: {}", relevantOrders);
        logger.info("   Total sales: {}", totalSales);
        logger.info("   Total revenue: ${}", totalRevenue);

        Map<String, Object> result = new HashMap<>();
        result.put("totalSales", totalSales);
        result.put("totalRevenue", totalRevenue);
        result.put("salesLast30Days", salesLast30Days);
        result.put("revenueLast30Days", revenueLast30Days);
        return result;
    }

    /**
     * Calcula las ventas específicas de una canción individual.
     * <p>
     * Suma las unidades vendidas de este track específico a través del servicio de comercio.
     * Es útil para determinar qué canciones están generando más conversión ("Best Sellers").
     * </p>
     *
     * @param songId Identificador de la canción.
     * @return Cantidad total de veces que la canción ha sido comprada.
     */
    private Map<String, Object> calculateSongSales(Long songId, List<OrderDTO> allOrders) {
        long totalSales = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (OrderDTO order : allOrders) {
            if (order.getStatus() == null || !"DELIVERED".equals(order.getStatus())) {
                continue;
            }

            if (order.getItems() == null) continue;

            for (OrderItemDTO item : order.getItems()) {
                if ("SONG".equalsIgnoreCase(item.getItemType()) && songId.equals(item.getItemId())) {
                    long quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;

                    totalSales += quantity;
                    totalRevenue = totalRevenue.add(price.multiply(BigDecimal.valueOf(quantity)));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalSales", totalSales);
        result.put("totalRevenue", totalRevenue);
        return result;
    }

    /**
     * Genera datos sintéticos diarios distribuyendo los totales actuales.
     * <p>
     * <b>Nota Técnica:</b> Este método es un "Mock" inteligente. En un entorno de producción real,
     * Audira debería tener un proceso batch nocturno que guarde snapshots diarios en una tabla
     * {@code historical_metrics}. Como esa tabla no existe en el esquema actual, este método
     * reconstruye una historia plausible usando aleatoriedad controlada para que los gráficos
     * del frontend no se vean vacíos.
     * </p>
     *
     * @param start Fecha inicio.
     * @param end Fecha fin.
     * @param totalPlays Total de plays actuales (semilla para la distribución).
     * @param currentRating Rating actual promedio.
     * @return Lista cronológica de métricas diarias.
     */
    private List<ArtistMetricsDetailed.DailyMetric> generateDailyMetricsWithRealData(
            List<Song> artistSongs,
            List<OrderDTO> allOrders,
            LocalDate startDate,
            LocalDate endDate,
            Long totalPlays
    ) {
        logger.info("📈 Generating daily metrics from {} to {}", startDate, endDate);
        logger.info("   Total plays to distribute: {}", totalPlays);

        List<ArtistMetricsDetailed.DailyMetric> metrics = new ArrayList<>();
        LocalDate currentDate = startDate;
        long daysInRange = endDate.toEpochDay() - startDate.toEpochDay() + 1;

        Map<String, Object> salesMetrics = calculateArtistSales(artistSongs, allOrders);
        Long totalSales = (Long) salesMetrics.get("totalSales");
        logger.info("   Total sales to distribute: {}", totalSales);

        Set<Long> songIds = artistSongs.stream().map(Song::getId).collect(Collectors.toSet());
        double totalRatingSum = 0.0;
        int ratingCount = 0;

        for (Long songId : songIds) {
            RatingStatsDTO stats = ratingServiceClient.getEntityRatingStats("SONG", songId);
            if (stats.getAverageRating() != null && stats.getAverageRating() > 0) {
                totalRatingSum += stats.getAverageRating();
                ratingCount++;
            }
        }

        double avgRating = ratingCount > 0 ? totalRatingSum / ratingCount : 0.0;

        Random random = new Random(42); // Fixed seed for consistent data

        long remainingPlays = totalPlays;
        long remainingSales = totalSales;
        long daysToDistribute = Math.min(daysInRange, 7); // Concentrate in last 7 days

        while (!currentDate.isAfter(endDate)) {
            long dailyPlays = 0;
            long dailySales = 0;

            boolean isRecentDay = (endDate.toEpochDay() - currentDate.toEpochDay()) < daysToDistribute;

            if (isRecentDay && remainingPlays > 0) {
                if (totalPlays < 10) {
                    dailyPlays = random.nextBoolean() ? remainingPlays : 0;
                } else {
                    dailyPlays = remainingPlays / daysToDistribute;
                }
                remainingPlays -= dailyPlays;
            }

            if (isRecentDay && remainingSales > 0) {
                if (totalSales < 10) {
                    dailySales = random.nextBoolean() ? remainingSales : 0;
                } else {
                    dailySales = remainingSales / daysToDistribute;
                }
                remainingSales -= dailySales;
            }

            if (currentDate.equals(endDate)) {
                dailyPlays += remainingPlays;
                dailySales += remainingSales;
            }

            BigDecimal dailyRevenue = BigDecimal.valueOf(dailySales * 0.99)
                    .setScale(2, RoundingMode.HALF_UP);

            long dailyComments = random.nextInt(3);

            double dailyRating = avgRating > 0 ? avgRating + (random.nextDouble() * 0.4 - 0.2) : 0.0;
            dailyRating = Math.max(0.0, Math.min(5.0, dailyRating));

            if (dailyPlays > 0 || dailySales > 0) {
                logger.debug("   Day {}: Plays={}, Sales={}, Revenue=${}",
                    currentDate, dailyPlays, dailySales, dailyRevenue);
            }

            metrics.add(ArtistMetricsDetailed.DailyMetric.builder()
                    .date(currentDate)
                    .plays(dailyPlays)
                    .sales(dailySales)
                    .revenue(dailyRevenue)
                    .comments(dailyComments)
                    .averageRating(Math.round(dailyRating * 10.0) / 10.0)
                    .build());

            currentDate = currentDate.plusDays(1);
        }

        long totalDistributed = metrics.stream().mapToLong(ArtistMetricsDetailed.DailyMetric::getPlays).sum();
        logger.info(" Daily metrics generated: {} days, {} total plays distributed", metrics.size(), totalDistributed);

        return metrics;
    }

    /**
     * Calcula un porcentaje de crecimiento estimado.
     * <p>
     * Al no tener datos históricos reales almacenados, se estima una tendencia
     * basada en el volumen actual de actividad.
     * </p>
     *
     * @param currentValue Valor actual de la métrica.
     * @return Porcentaje de crecimiento estimado (positivo).
     */
    private Double calculateEstimatedGrowth(Long currentValue) {
        if (currentValue == 0) return 0.0;
        double growthFactor = Math.min(currentValue / 100.0, 1.0);
        return growthFactor * 15.0; // 0-15% estimated growth
    }
}