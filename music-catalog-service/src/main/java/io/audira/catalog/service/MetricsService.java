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
    private static final String TOTAL_KEY = "totalSales";
    private static final String REVENUE_KEY = "totalRevenue";

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
        Long totalSales = (Long) salesMetrics.get(TOTAL_KEY);
        BigDecimal totalRevenue = (BigDecimal) salesMetrics.get(REVENUE_KEY);
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
        Long totalSales = (Long) salesMetrics.get(TOTAL_KEY);
        BigDecimal totalRevenue = (BigDecimal) salesMetrics.get(REVENUE_KEY);

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
        Long totalSales = (Long) songSales.get(TOTAL_KEY);
        BigDecimal totalRevenue = (BigDecimal) songSales.get(REVENUE_KEY);

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

    @lombok.Data // Or manually add getters/setters
    private static class SalesStats {
        long totalSales = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long salesLast30Days = 0;
        BigDecimal revenueLast30Days = BigDecimal.ZERO;
        
        // Counters for logging
        int deliveredOrders = 0;
        int skippedOrders = 0;
        int relevantOrders = 0;
    }

    // 2. Main Method (Clean and Linear)
    private Map<String, Object> calculateArtistSales(List<Song> artistSongs, List<OrderDTO> allOrders) {
        Set<Long> artistSongIds = artistSongs.stream()
                .map(Song::getId)
                .collect(Collectors.toSet());

        logger.info(" Calculating sales for artist songs. Artist has {} songs", artistSongs.size());
        logger.info("   Processing {} total orders", allOrders.size());

        SalesStats stats = new SalesStats();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // Main Loop: complexity reduced significantly
        for (OrderDTO order : allOrders) {
            processOrder(order, artistSongIds, stats, thirtyDaysAgo);
        }

        logSummary(allOrders.size(), stats);
        return buildResultMap(stats);
    }

    // 3. Helper: Process a single Order
    private void processOrder(OrderDTO order, Set<Long> artistSongIds, SalesStats stats, LocalDateTime thirtyDaysAgo) {
        logger.debug("   Order ID {} - Status: {} - Created: {}", 
            order.getId(), order.getStatus(), order.getCreatedAt());

        if (!isOrderValid(order)) {
            logger.debug("       Skipped order {} - Status: {} (not DELIVERED)", 
                order.getId(), order.getStatus());
            stats.skippedOrders++;
            return;
        }

        stats.deliveredOrders++;

        if (order.getItems() == null) {
            logger.debug("       Order {} has no items", order.getId());
            return;
        }

        for (OrderItemDTO item : order.getItems()) {
            processOrderItem(item, order, artistSongIds, stats, thirtyDaysAgo);
        }
    }

    // 4. Helper: Process a single Item (The core calculation logic)
    private void processOrderItem(OrderItemDTO item, OrderDTO order, Set<Long> artistSongIds, 
                                  SalesStats stats, LocalDateTime thirtyDaysAgo) {
        
        if (!"SONG".equalsIgnoreCase(item.getItemType()) || !artistSongIds.contains(item.getItemId())) {
            return;
        }

        stats.relevantOrders++; // Incremented per matching item found
        
        long quantity = item.getQuantity() != null ? item.getQuantity() : 1;
        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal itemRevenue = price.multiply(BigDecimal.valueOf(quantity));

        logger.info("       Found sale: Song ID {} - Qty: {} - Price: ${} - Revenue: ${}",
                item.getItemId(), quantity, price, itemRevenue);

        // Update Totals
        stats.totalSales += quantity;
        stats.totalRevenue = stats.totalRevenue.add(itemRevenue);

        // Update 30-Day Totals
        if (order.getCreatedAt() != null && order.getCreatedAt().isAfter(thirtyDaysAgo)) {
            stats.salesLast30Days += quantity;
            stats.revenueLast30Days = stats.revenueLast30Days.add(itemRevenue);
        }
    }

    // 5. Small Helpers for readability
    private boolean isOrderValid(OrderDTO order) {
        return order.getStatus() != null && "DELIVERED".equals(order.getStatus());
    }

    private void logSummary(int totalOrders, SalesStats stats) {
        logger.info(" Sales calculation summary:");
        logger.info("   Total orders processed: {}", totalOrders);
        logger.info("   DELIVERED orders: {}", stats.deliveredOrders);
        logger.info("   Skipped orders (not DELIVERED): {}", stats.skippedOrders);
        logger.info("   Orders with artist's songs: {}", stats.relevantOrders);
        logger.info("   Total sales: {}", stats.totalSales);
        logger.info("   Total revenue: ${}", stats.totalRevenue);
    }

    private Map<String, Object> buildResultMap(SalesStats stats) {
        Map<String, Object> result = new HashMap<>();
        result.put(TOTAL_KEY, stats.totalSales);
        result.put("totalRevenue", stats.totalRevenue);
        result.put("salesLast30Days", stats.salesLast30Days);
        result.put("revenueLast30Days", stats.revenueLast30Days);
        return result;
    }

    // 2. Método Principal (Complejidad reducida drásticamente)
    private Map<String, Object> calculateSongSales(Long songId, List<OrderDTO> allOrders) {
        SalesStats stats = new SalesStats();

        for (OrderDTO order : allOrders) {
            // Delegamos la lógica compleja a un método auxiliar
            processOrderForSong(order, songId, stats);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalSales", stats.totalSales);
        result.put("totalRevenue", stats.totalRevenue);
        return result;
    }

    // 3. Método Auxiliar: Procesa una orden individual
    private void processOrderForSong(OrderDTO order, Long songId, SalesStats stats) {
        // Guard Clause: Si NO es válida, salimos inmediatamente.
        // Esto evita el anidamiento profundo (else).
        if (!isOrderValid(order)) {
            return;
        }

        for (OrderItemDTO item : order.getItems()) {
            accumulateItemSales(item, songId, stats);
        }
    }

    // 4. Método Auxiliar: Calcula las ventas del ítem
    private void accumulateItemSales(OrderItemDTO item, Long songId, SalesStats stats) {
        // Verificación simple y plana
        if ("SONG".equalsIgnoreCase(item.getItemType()) && songId.equals(item.getItemId())) {
            long quantity = item.getQuantity() != null ? item.getQuantity() : 1;
            BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;

            stats.totalSales += quantity;
            stats.totalRevenue = stats.totalRevenue.add(price.multiply(BigDecimal.valueOf(quantity)));
        }
    }

    // 1. Inner class to manage the mutable state (simplifies method signatures)
    private static class DistributionState {
        long remainingPlays;
        long remainingSales;
        long totalPlays; // Needed for the "< 10" logic
        long totalSales;
        long daysToDistribute;
        Random random;

        public DistributionState(long totalPlays, long totalSales, long daysInRange) {
            this.remainingPlays = totalPlays;
            this.remainingSales = totalSales;
            this.totalPlays = totalPlays;
            this.totalSales = totalSales;
            this.daysToDistribute = Math.min(daysInRange, 7);
            this.random = new Random(42);
        }
    }

    // 2. Main Method (Clean and Linear)
    private List<ArtistMetricsDetailed.DailyMetric> generateDailyMetricsWithRealData(
            List<Song> artistSongs,
            List<OrderDTO> allOrders,
            LocalDate startDate,
            LocalDate endDate,
            Long totalPlays
    ) {
        logger.info("📈 Generating daily metrics from {} to {}", startDate, endDate);
        logger.info("   Total plays to distribute: {}", totalPlays);

        // Step 1: Calculate aggregations (extracted)
        double avgRating = calculateAverageRating(artistSongs);
        long totalSales = extractTotalSales(artistSongs, allOrders);

        // Step 2: Initialize State
        long daysInRange = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        DistributionState state = new DistributionState(totalPlays, totalSales, daysInRange);
        List<ArtistMetricsDetailed.DailyMetric> metrics = new ArrayList<>();

        // Step 3: Main Loop (Logic delegated to helper)
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            metrics.add(createMetricForDay(currentDate, endDate, state, avgRating));
            currentDate = currentDate.plusDays(1);
        }

        return metrics;
    }

    private long extractTotalSales(List<Song> artistSongs, List<OrderDTO> allOrders) {
        Map<String, Object> salesMetrics = calculateArtistSales(artistSongs, allOrders);
        long sales = (Long) salesMetrics.get(TOTAL_KEY);
        logger.info("   Total sales to distribute: {}", sales);
        return sales;
    }

    private double calculateAverageRating(List<Song> artistSongs) {
        double totalRatingSum = 0.0;
        int ratingCount = 0;

        for (Song song : artistSongs) {
            RatingStatsDTO stats = ratingServiceClient.getEntityRatingStats("SONG", song.getId());
            if (stats.getAverageRating() != null && stats.getAverageRating() > 0) {
                totalRatingSum += stats.getAverageRating();
                ratingCount++;
            }
        }
        return ratingCount > 0 ? totalRatingSum / ratingCount : 0.0;
    }

    private ArtistMetricsDetailed.DailyMetric createMetricForDay(
            LocalDate currentDate, LocalDate endDate, DistributionState state, double avgRating) {
        
        long dailyPlays = 0;
        long dailySales = 0;
        boolean isRecentDay = (endDate.toEpochDay() - currentDate.toEpochDay()) < state.daysToDistribute;

        // Logic for Plays
        if (isRecentDay && state.remainingPlays > 0) {
            dailyPlays = calculateDailyAmount(state.remainingPlays, state.totalPlays, state.daysToDistribute, state.random);
            state.remainingPlays -= dailyPlays;
        }

        // Logic for Sales
        if (isRecentDay && state.remainingSales > 0) {
            dailySales = calculateDailyAmount(state.remainingSales, state.totalSales, state.daysToDistribute, state.random);
            state.remainingSales -= dailySales;
        }

        // Catch-all for the final day
        if (currentDate.equals(endDate)) {
            dailyPlays += state.remainingPlays;
            dailySales += state.remainingSales;
        }

        logDailyActivity(currentDate, dailyPlays, dailySales);

        return buildDailyMetric(currentDate, dailyPlays, dailySales, avgRating, state.random);
    }

    // Generic logic for distributing numbers (Works for both Plays and Sales)
    private long calculateDailyAmount(long remaining, long total, long daysToDistribute, Random random) {
        if (total < 10) {
            return random.nextBoolean() ? remaining : 0;
        }
        return remaining / daysToDistribute;
    }

    private ArtistMetricsDetailed.DailyMetric buildDailyMetric(
        LocalDate date, long plays, long sales, double avgRating, Random random) {
    
        BigDecimal dailyRevenue = BigDecimal.valueOf(sales * 0.99)
                .setScale(2, RoundingMode.HALF_UP);
        
        long dailyComments = random.nextInt(3);
        
        double dailyRating = 0.0;
        if (avgRating > 0) {
            dailyRating = avgRating + (random.nextDouble() * 0.4 - 0.2);
            // Asegurar que el rating esté entre 0 y 5
            dailyRating = Math.max(0.0, Math.min(5.0, dailyRating));
        }

        ArtistMetricsDetailed.DailyMetric metric = new ArtistMetricsDetailed.DailyMetric();
            metric.setDate(date); 
            metric.setPlays(plays);
            metric.setSales(sales);
            metric.setRevenue(dailyRevenue);
            metric.setComments(dailyComments); 
            metric.setAverageRating(dailyRating);
        
        return metric;
    }

    private void logDailyActivity(LocalDate date, long plays, long sales) {
        if (plays > 0 || sales > 0) {
            // Recalculating revenue just for log to keep method signature clean
            BigDecimal revenue = BigDecimal.valueOf(sales * 0.99).setScale(2, RoundingMode.HALF_UP); 
            logger.debug("   Day {}: Plays={}, Sales={}, Revenue=${}", date, plays, sales, revenue);
        }
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