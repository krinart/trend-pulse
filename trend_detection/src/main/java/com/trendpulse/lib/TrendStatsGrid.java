package com.trendpulse.lib;

import com.trendpulse.schema.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

class GridCell {
    private final double lat;
    private final double lon;
    private int count;

    public GridCell(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        this.count = 0;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        this.count++;
    }
}

public class TrendStatsGrid {
    private final int windowMinutes;
    private final int[] zooms;
    private final int[] maxPointsPerTile;
    private final int maxZoom;
    private final int cellSizeMeters;
    private final double latStep;
    private final double lonStep;
    private final Map<String, Map<String, GridCell>> cells;
    private final Random random;

    private static final int DEFAULT_CELL_SIZE_METERS = 1000;
    private static final int[] DEFAULT_ZOOMS = {3, 6, 9, 12};
    private static final int[] DEFAULT_MAX_POINTS_PER_TILE = {5, 20, 50, 100};

    public TrendStatsGrid(int windowMinutes) {
        this(windowMinutes, DEFAULT_ZOOMS, DEFAULT_MAX_POINTS_PER_TILE, DEFAULT_CELL_SIZE_METERS);
    }

    public TrendStatsGrid(int windowMinutes, int[] zooms, int[] maxPointsPerTile, int cellSizeMeters) {
        this.windowMinutes = windowMinutes;
        this.zooms = zooms;
        this.maxPointsPerTile = maxPointsPerTile;
        this.maxZoom = Arrays.stream(zooms).max().getAsInt();
        this.cellSizeMeters = cellSizeMeters;
        
        double[] steps = Utils.metersToDegreesArray(cellSizeMeters);
        this.latStep = steps[0];
        this.lonStep = steps[1];
        
        this.cells = new HashMap<>();
        this.random = new Random();
    }

    private String getCellKey(double lat, double lon) {
        return String.format("%.4f:%.4f", lat, lon);
    }

    private double[] getCellCoords(double lat, double lon) {
        double cellLat = Math.round(Math.round(lat / latStep) * latStep * 10000.0) / 10000.0;
        double cellLon = Math.round(Math.round(lon / lonStep) * lonStep * 10000.0) / 10000.0;
        return new double[]{cellLat, cellLon};
    }

    public void addPoint(Instant timestamp, double lat, double lon) {
        String windowTs = TimeUtils.timestampToWindowStart(timestamp, windowMinutes).toString();
        
        double[] cellCoords = getCellCoords(lat, lon);
        String cellKey = getCellKey(cellCoords[0], cellCoords[1]);
        
        cells.putIfAbsent(windowTs, new HashMap<>());
        Map<String, GridCell> windowCells = cells.get(windowTs);
        
        windowCells.putIfAbsent(cellKey, new GridCell(cellCoords[0], cellCoords[1]));
        windowCells.get(cellKey).incrementCount();
    }

    public WindowStats getWindowStats(Instant windowStart) {
        String ts = windowStart.toString();
        WindowStats stats = new WindowStats();
        
        stats.setWindowStart(windowStart.getEpochSecond());
        stats.setWindowEnd(windowStart.plusSeconds(windowMinutes * 60L).getEpochSecond());
        
        if (!cells.containsKey(ts)) {
            stats.setCount(0);
            stats.setGeoStats(Collections.emptyList());
        } else {
            populateWindowStats(stats, cells.get(ts).values());
        }
        
        return stats;
    }

    private WindowStats populateWindowStats(WindowStats windowStats, Collection<GridCell> gridCells) {
        int totalCount = gridCells.stream().mapToInt(GridCell::getCount).sum();
        List<ZoomStats> zoomStatsList = new ArrayList<>();

        for (int i = 0; i < zooms.length; i++) {
            int zoom = zooms[i];
            int maxPoints = maxPointsPerTile[i];

            Map<String, TileData> tiles = new HashMap<>();
            
            for (GridCell cell : gridCells) {
                int[] tileCoords = Utils.latLonToTile(cell.getLat(), cell.getLon(), zoom);
                String tileKey = tileCoords[0] + ":" + tileCoords[1];
                
                tiles.putIfAbsent(tileKey, new TileData());
                TileData tileData = tiles.get(tileKey);
                tileData.totalCount += cell.getCount();
                tileData.points.add(createPoint(cell.getLat(), cell.getLon(), cell.getCount()));
            }

            List<TileStats> tileStatsList = new ArrayList<>();
            for (Map.Entry<String, TileData> entry : tiles.entrySet()) {
                String[] coords = entry.getKey().split(":");
                TileData tileData = entry.getValue();
                List<Point> points = tileData.points;

                if (!points.isEmpty()) {
                    int sampleSize = Math.min(maxPoints, points.size());
                    List<Point> sampledPoints = samplePoints(points, sampleSize);
                    
                    TileStats tileStats = new TileStats();
                    tileStats.setTileX(Integer.parseInt(coords[0]));
                    tileStats.setTileY(Integer.parseInt(coords[1]));
                    tileStats.setTotalCount(tileData.totalCount);
                    tileStats.setPointsCount(points.size());
                    tileStats.setSampledPoints(sampledPoints);
                    
                    tileStatsList.add(tileStats);
                }
            }

            ZoomStats zoomStats = new ZoomStats();
            zoomStats.setZoom(zoom);
            zoomStats.setStats(tileStatsList);
            zoomStatsList.add(zoomStats);
        }

        windowStats.setCount(totalCount);
        windowStats.setGeoStats(zoomStatsList);
        return windowStats;
    }

    private Point createPoint(double lat, double lon, int count) {
        Point point = new Point();
        point.setLat(lat);
        point.setLon(lon);
        point.setCount(count);
        return point;
    }

    private List<Point> samplePoints(List<Point> points, int sampleSize) {
        if (points.size() <= sampleSize) {
            return new ArrayList<>(points);
        }
        
        return points.stream()
            .sorted((p1, p2) -> Integer.compare(p2.getCount(), p1.getCount()))
            .limit(sampleSize)
            .collect(Collectors.toList());
    }

    private static class TileData {
        int totalCount = 0;
        List<Point> points = new ArrayList<>();
    }
}

// Utils class containing static helper methods
class Utils {
    public static double[] metersToDegreesArray(double meters) {
        // Earth's radius in meters
        double earthRadius = 6371000;
        
        // 1 degree latitude is approximately 111km
        double latDegrees = (meters / 111000);
        
        // 1 degree longitude is also approximated as 111km
        // In reality it varies with latitude
        double lonDegrees = (meters / 111000);
        
        return new double[]{latDegrees, lonDegrees};
    }

    public static Instant timestampToWindowStart(Instant timestamp, int windowMinutes) {
        long epochSeconds = timestamp.getEpochSecond();
        long windowSeconds = windowMinutes * 60L;
        long windowStartSeconds = (epochSeconds / windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(windowStartSeconds);
    }

    public static int[] latLonToTile(double lat, double lon, int zoom) {
        double n = Math.pow(2.0, zoom);
        
        // Ensure lon is in range [-180, 180]
        lon = ((lon + 180) % 360) - 180;
        
        // Convert to tile coordinates
        int x = (int) (((lon + 180) / 360.0) * n);
        double latRad = Math.toRadians(lat);
        int y = (int) ((1.0 - asinh(Math.tan(latRad)) / Math.PI) / 2.0 * n);
        
        // Ensure coordinates are within bounds
        x = Math.max(0, Math.min((int) (n - 1), x));
        y = Math.max(0, Math.min((int) (n - 1), y));
        
        return new int[]{x, y};
    }

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }
}