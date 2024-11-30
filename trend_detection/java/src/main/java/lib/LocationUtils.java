import java.util.List;
import java.util.Arrays;


public class LocationUtils {
    private static final List<Location> ALL_LOCATIONS = Arrays.asList(
        // California
        new Location(1, 32.7157, -117.1611, "San Diego", "CA", "California"),
        new Location(2, 34.0522, -118.2437, "Los Angeles", "CA", "California"),
        new Location(3, 37.7749, -122.4194, "Bay Area", "CA", "California"),
        
        // Florida
        new Location(4, 28.5384, -81.3789, "Orlando", "FL", "Florida"),
        new Location(5, 25.7617, -80.1918, "Miami", "FL", "Florida"),
        
        // PNW (Pacific Northwest)
        new Location(6, 47.6062, -122.3321, "Seattle", "WA", "PNW"),
        new Location(7, 45.5155, -122.6789, "Portland", "OR", "PNW"),
        
        // Great Lakes
        new Location(8, 41.8781, -87.6298, "Chicago", "IL", "Great Lakes"),
        new Location(9, 40.4406, -79.9959, "Pittsburgh", "PA", "Great Lakes"),
        new Location(10, 42.3314, -83.0458, "Detroit", "MI", "Great Lakes"),
        new Location(11, 39.9612, -82.9988, "Columbus", "OH", "Great Lakes"),
        
        // Northeast Corridor
        new Location(12, 39.9526, -75.1652, "Philadelphia", "PA", "Northeast Corridor"),
        new Location(13, 39.2904, -76.6122, "Baltimore", "MD", "Northeast Corridor"),
        new Location(14, 42.3601, -71.0589, "Boston", "MA", "Northeast Corridor"),
        new Location(15, 38.9072, -77.0369, "Washington", "DC", "Northeast Corridor"),
        new Location(16, 40.7128, -74.0060, "New York", "NY", "Great Lakes"),
        
        // Texas Triangle
        new Location(17, 30.2672, -97.7431, "Austin", "TX", "Texas Triangle"),
        new Location(18, 32.7767, -96.7970, "Dallas", "TX", "Texas Triangle"),
        new Location(19, 29.7604, -95.3698, "Houston", "TX", "Texas Triangle"),
        new Location(20, 29.4241, -98.4936, "San Antonio", "TX", "Texas Triangle")
    );

    private static final double MAX_DISTANCE_KM = 100.0; // Maximum distance threshold

    public static Integer findNearestLocation(double lat, double lon) {
        double minDistance = Double.MAX_VALUE;
        Integer nearestLocationId = null;

        for (Location location : ALL_LOCATIONS) {
            double distance = calculateHaversineDistance(
                lat, lon, 
                location.getLat(), location.getLon()
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearestLocationId = location.getId();
            }
        }

        // Return null if no location is within MAX_DISTANCE_KM
        return minDistance <= MAX_DISTANCE_KM ? nearestLocationId : null;
    }

    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth's radius in kilometers

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}