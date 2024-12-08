package com.trendpulse.items;

import java.time.Instant;
import java.util.*;

import com.trendpulse.schema.Point;
import com.trendpulse.schema.TileStats;
import com.trendpulse.schema.WindowStats;
import com.trendpulse.schema.ZoomStats;

public class GlobalTrend implements Trend {
    private String id;
    private String name;
    private String topic;
    private Set<LocalTrend> localTrends;
    private List<String> keywords;
    private double[] centroid;
    private ArrayList<Integer> locationIds = new ArrayList<>();
    Map<Instant, WindowStats> windowStats = new HashMap<>();

    public GlobalTrend(String id, Set<LocalTrend> localTrends) {
        this.id = id;

        this.localTrends = localTrends;
        
        // Combine keywords from all local trends
        this.keywords = new ArrayList<>();
        for (LocalTrend trend : localTrends) {
            this.keywords.addAll(trend.getKeywords());
            this.topic = trend.getTopic();
            this.locationIds.add(trend.getLocationId());
        }

        if (this.topic == null) {
            throw new IllegalStateException("Global trend has null topic");
        }

        initializeWindowStats();

        // Calculate average centroid and coordinates
        this.centroid = calculateAverageCentroid();
    }

    private void initializeWindowStats() {
        for (LocalTrend trend : localTrends) {
            for (WindowStats ws : trend.getWindowStatsAll()) {
                this.addLocalTrendWindowStats(Instant.ofEpochSecond(ws.getWindowStart()), ws);
            }
        }
    }

    private double[] calculateAverageCentroid() {
        if (localTrends.isEmpty()) return new double[0];
        
        LocalTrend firstTrend = localTrends.iterator().next();
        double[] avgCentroid = new double[firstTrend.getCentroid().length];
        
        for (LocalTrend trend : localTrends) {
            double[] trendCentroid = trend.getCentroid();
            for (int i = 0; i < avgCentroid.length; i++) {
                avgCentroid[i] += trendCentroid[i];
            }
        }
        
        for (int i = 0; i < avgCentroid.length; i++) {
            avgCentroid[i] /= localTrends.size();
        }
        
        return avgCentroid;
    }

    public void addLocalTrend(LocalTrend newTrend) {
        this.localTrends.add(newTrend);
        this.keywords.addAll(newTrend.getKeywords());
        this.centroid = calculateAverageCentroid();
    }

    public WindowStats getWindowStats(Instant ts) {
        return windowStats.get(ts);
    }

    public void addLocalTrendWindowStats(Instant windowTs, WindowStats localTrendWindowStat) {
        WindowStats existingStats = this.windowStats.computeIfAbsent(windowTs, k -> {
            WindowStats newStats = new WindowStats();
            newStats.setWindowStart(localTrendWindowStat.getWindowStart());
            newStats.setWindowEnd(localTrendWindowStat.getWindowEnd());
            newStats.setCount(0);
            newStats.setGeoStats(new ArrayList<>());
            return newStats;
        });
    
        // Add counts
        existingStats.setCount(existingStats.getCount() + localTrendWindowStat.getCount());
    
        // Merge geo stats for each zoom level
        Map<Integer, ZoomStats> existingZoomStats = new HashMap<>();
        for (ZoomStats zs : existingStats.getGeoStats()) {
            existingZoomStats.put(zs.getZoom(), zs);
        }
    
        for (ZoomStats newZoomStats : localTrendWindowStat.getGeoStats()) {
            ZoomStats existingZoomStat = existingZoomStats.get(newZoomStats.getZoom());
            
            if (existingZoomStat == null) {
                // If no existing stats for this zoom level, add the new one directly
                existingStats.getGeoStats().add(newZoomStats);
                continue;
            }
    
            // Merge tile stats for this zoom level
            Map<TileKey, TileStats> existingTileStats = new HashMap<>();
            for (TileStats ts : existingZoomStat.getStats()) {
                existingTileStats.put(new TileKey(ts.getTileX(), ts.getTileY()), ts);
            }
    
            for (TileStats newTileStats : newZoomStats.getStats()) {
                TileKey tileKey = new TileKey(newTileStats.getTileX(), newTileStats.getTileY());
                TileStats existingTileStat = existingTileStats.get(tileKey);
    
                if (existingTileStat == null) {
                    existingZoomStat.getStats().add(newTileStats);
                } else {
                    // Merge tile statistics
                    mergeTileStats(existingTileStat, newTileStats);
                }
            }
        }
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String getTopic() { return topic; }
    @Override public List<String> getKeywords() { return keywords; }
    @Override public double[] getCentroid() { return centroid; }
    public ArrayList<Integer> getLocationIds() { return locationIds; }

    public Set<LocalTrend> getLocalTrends() { return localTrends; }
    public void setName(String name) { this.name = name; }

    // Helper class to use tile coordinates as map key
private static class TileKey {
    private final int x;
    private final int y;

    public TileKey(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TileKey tileKey = (TileKey) o;
        return x == tileKey.x && y == tileKey.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

private void mergeTileStats(TileStats existing, TileStats newStats) {
    // Update counts
    existing.setTotalCount(existing.getTotalCount() + newStats.getTotalCount());
    existing.setPointsCount(existing.getPointsCount() + newStats.getPointsCount());

    // Merge sampled points
    List<Point> mergedPoints = new ArrayList<>(existing.getSampledPoints());
    mergedPoints.addAll(newStats.getSampledPoints());
    
    // If we have too many points, randomly subsample them
    if (mergedPoints.size() > 100) { // Adjust sample size as needed
        Collections.shuffle(mergedPoints);
        mergedPoints = mergedPoints.subList(0, 100);
    }
    
    existing.setSampledPoints(mergedPoints);
}
}
