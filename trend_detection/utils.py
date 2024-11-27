from datetime import datetime
from typing import Tuple
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


def timestamp_to_window_start(timestamp: datetime, window_minutes: int) -> datetime:
    """
    Rounds down a timestamp to the start of its time window.
    
    Args:
        timestamp: The datetime to round down
        window_minutes: Size of the time window in minutes
    
    Returns:
        datetime: Start of the time window
    
    Example:
        timestamp_to_window_start(datetime(2024, 1, 1, 1, 17), 15)
        -> datetime(2024, 1, 1, 1, 15)  # rounds down to nearest 15 min window
    """
    minutes_since_midnight = timestamp.hour * 60 + timestamp.minute
    window_start_minutes = (minutes_since_midnight // window_minutes) * window_minutes
    
    return timestamp.replace(
        hour=window_start_minutes // 60,
        minute=window_start_minutes % 60,
        second=0,
        microsecond=0
    )


def lat_lon_to_tile(lat: float, lon: float, zoom: int) -> Tuple[int, int]:
    """Convert latitude/longitude to tile coordinates"""
    n = 2.0 ** zoom
    # Ensure lon is in range [-180, 180]
    lon = ((lon + 180) % 360) - 180
    # Convert to tile coordinates
    x = int(((lon + 180) / 360.0) * n)
    lat_rad = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    # Ensure coordinates are within bounds
    x = max(0, min(int(n - 1), x))
    y = max(0, min(int(n - 1), y))
    return x, y