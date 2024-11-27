import math


def find_nearest_location(lat: float, lon: float, locations) -> int:
        """Find the nearest location ID given a latitude and longitude"""
        min_distance = float('inf')
        nearest_location_id = None
        
        for location in locations:
            distance = haversine_distance(lat, lon, location.lat, location.lon)
            if distance < min_distance:
                min_distance = distance
                nearest_location_id = location.id

        return nearest_location_id if min_distance <= 100 else None


def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculate distance between two points on Earth using Haversine formula"""
        R = 6371  # Earth's radius in kilometers

        # Convert latitude and longitude to radians
        lat1, lon1, lat2, lon2 = map(math.radians, [lat1, lon1, lat2, lon2])

        # Haversine formula
        dlat = lat2 - lat1
        dlon = lon2 - lon1
        a = math.sin(dlat/2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon/2)**2
        c = 2 * math.asin(math.sqrt(a))

        return R * c
