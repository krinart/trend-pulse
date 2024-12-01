package com.trendpulse.lib;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;


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

class Point {
    @JsonProperty("lat")
    private final double lat;
    
    @JsonProperty("lon")
    private final double lon;
    
    @JsonProperty("count")
    private final int count;

    public Point(double lat, double lon, int count) {
        this.lat = lat;
        this.lon = lon;
        this.count = count;
    }
}

class TileStats {
    @JsonProperty("tile_x")
    private final int tileX;
    
    @JsonProperty("tile_y")
    private final int tileY;
    
    @JsonProperty("total_count")
    private final int totalCount;
    
    @JsonProperty("points_count")
    private final int pointsCount;
    
    @JsonProperty("sampled_points")
    private final List<Point> sampledPoints;

    public TileStats(int tileX, int tileY, int totalCount, int pointsCount, List<Point> sampledPoints) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.totalCount = totalCount;
        this.pointsCount = pointsCount;
        this.sampledPoints = sampledPoints;
    }
}

class ZoomStats {
    @JsonProperty("zoom")
    private final int zoom;
    
    @JsonProperty("stats")
    private final List<TileStats> stats;

    public ZoomStats(int zoom, List<TileStats> stats) {
        this.zoom = zoom;
        this.stats = stats;
    }
}

class WindowStats {
    @JsonProperty("count")
    private final int count;
    
    @JsonProperty("geo_stats")
    private final List<ZoomStats> geoStats;

    public WindowStats(int count, List<ZoomStats> geoStats) {
        this.count = count;
        this.geoStats = geoStats;
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

    private static final int  DEFAULT_CELL_SIZE_METERS = 1000;
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
        
        // Calculate grid cell size in degrees
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
        String windowTs = Utils.timestampToWindowStart(timestamp, windowMinutes).toString();
        
        // Get grid cell coordinates
        double[] cellCoords = getCellCoords(lat, lon);
        String cellKey = getCellKey(cellCoords[0], cellCoords[1]);
        
        // Initialize maps if necessary
        cells.putIfAbsent(windowTs, new HashMap<>());
        Map<String, GridCell> windowCells = cells.get(windowTs);
        
        // Create or update cell
        windowCells.putIfAbsent(cellKey, new GridCell(cellCoords[0], cellCoords[1]));
        windowCells.get(cellKey).incrementCount();
    }

    public WindowStats getWindowStats(Instant windowStart) {
        String ts = windowStart.toString();
        if (!cells.containsKey(ts)) {
            return new WindowStats(0, Collections.emptyList());
        }

        List<GridCell> gridCells = new ArrayList<>(cells.get(ts).values());
        int totalCount = gridCells.stream().mapToInt(GridCell::getCount).sum();
        List<ZoomStats> result = new ArrayList<>();

        for (int i = 0; i < zooms.length; i++) {
            int zoom = zooms[i];
            int maxPoints = maxPointsPerTile[i];

            // Create tiles
            Map<String, TileData> tiles = new HashMap<>();
            
            for (GridCell cell : gridCells) {
                int[] tileCoords = Utils.latLonToTile(cell.getLat(), cell.getLon(), zoom);
                String tileKey = tileCoords[0] + ":" + tileCoords[1];
                
                tiles.putIfAbsent(tileKey, new TileData());
                TileData tileData = tiles.get(tileKey);
                tileData.totalCount += cell.getCount();
                tileData.points.add(new Point(cell.getLat(), cell.getLon(), cell.getCount()));
            }

            // Sample points for each tile
            List<TileStats> stats = new ArrayList<>();
            for (Map.Entry<String, TileData> entry : tiles.entrySet()) {
                String[] coords = entry.getKey().split(":");
                TileData tileData = entry.getValue();
                List<Point> points = tileData.points;

                if (!points.isEmpty()) {
                    int sampleSize = Math.min(maxPoints, points.size());
                    List<Point> sampledPoints = samplePoints(points, sampleSize);
                    
                    stats.add(new TileStats(
                        Integer.parseInt(coords[0]),
                        Integer.parseInt(coords[1]),
                        tileData.totalCount,
                        points.size(),
                        sampledPoints
                    ));
                }
            }

            result.add(new ZoomStats(zoom, stats));
        }

        return new WindowStats(totalCount, result);
    }

    private List<Point> samplePoints(List<Point> points, int sampleSize) {
        if (points.size() <= sampleSize) {
            return new ArrayList<>(points);
        }
        
        List<Point> shuffled = new ArrayList<>(points);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, sampleSize);
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