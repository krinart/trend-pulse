import numpy as np
from datetime import datetime, timedelta
import numpy as np
from shapely.geometry import Point, Polygon
from typing import List, Tuple, Union
from pyproj import Transformer


def generate_trend_timeseries(hours_duration, window_minutes, num_peaks, seed=None,
                              start_time=None):
    if seed is not None:
        np.random.seed(seed)
    
    total_minutes = hours_duration * 60
    num_points = total_minutes // window_minutes
    timeline = np.linspace(0, total_minutes, num_points)
    
    # Generate natural growth and decay curve but shifted towards end
    lifecycle = 1 / (1 + np.exp(-(timeline - total_minutes*0.7) / (total_minutes/8)))  # Shifted to 70% of duration
    lifecycle = lifecycle * (1 - lifecycle)
    lifecycle = lifecycle / np.max(lifecycle)
    
    # Generate peaks
    peaks = np.zeros(num_points)
    
    # Force last peak to be near the end
    end_peak_loc = int(num_points * 0.95)  # Place at 95% of duration
    end_peak_width = num_points // 15  # Slightly narrower peak
    end_peak_height = np.random.uniform(0.8, 1.0)  # Ensure it's a strong peak
    
    x = np.arange(num_points)
    end_peak = end_peak_height * np.exp(-(x - end_peak_loc)**2 / (2 * end_peak_width**2))
    peaks += end_peak
    
    # Add remaining peaks
    for _ in range(num_peaks - 1):
        # Random peak location in first 80% of timeline
        peak_loc = np.random.randint(num_points // (num_peaks + 1), 
                                   int(num_points * 0.8))
        peak_width = np.random.randint(num_points // 20, num_points // 10)
        peak_height = np.random.uniform(0.5, 1.0)
        
        peak = peak_height * np.exp(-(x - peak_loc)**2 / (2 * peak_width**2))
        peaks += peak
    
    # Rest of the function remains the same
    peaks = peaks / np.max(peaks)
    values = lifecycle * (1 + peaks * 2)
    
    noise = np.random.normal(0, 0.05, num_points)
    values += noise
    values = np.maximum(0, values)
    values = values / np.max(values)
    
    start_time = start_time or datetime.now().replace(microsecond=0, second=0, minute=0)
    
    data = []
    for i in range(num_points):
        timestamp = start_time + timedelta(minutes=i * window_minutes)
        data.append({
            'timestamp': timestamp.isoformat(),
            'value': values[i]
        })
    
    return data


def generate_random_points_in_polygon(
    polygon: Union[Polygon, str],
    num_points: int,
    seed: int = None
) -> List[Tuple[float, float]]:
    """
    Generate random points within a Shapely polygon.
    
    Args:
        polygon: Shapely polygon object or WKT string
        num_points: Number of points to generate
        seed: Random seed for reproducibility
    
    Returns:
        List of tuples containing (longitude, latitude) coordinates
    """
    if seed is not None:
        np.random.seed(seed)
    
    # Convert WKT to Polygon if needed
    if isinstance(polygon, str):
        from shapely import wkt
        polygon = wkt.loads(polygon)
    
    # Get the bounds of the polygon
    minx, miny, maxx, maxy = polygon.bounds
    
    points = []
    while len(points) < num_points:
        # Generate a random point within the bounding box
        point = Point(
            np.random.uniform(minx, maxx),
            np.random.uniform(miny, maxy)
        )
        
        # Add the point if it's within the polygon
        if polygon.contains(point):
            points.append({"coordinates": (point.x, point.y)})
    
    return points


def points_to_geojson(points: List[Tuple[float, float]], from_epsg: int = 3857, use_intensities=False) -> dict:
    """
    Convert list of points to GeoJSON format, transforming coordinates if needed.
    
    Args:
        points: List of coordinate tuples. If from_epsg=3857, these should be (x, y) Web Mercator coordinates.
                                         If from_epsg=4326, these should be (longitude, latitude) coordinates.
        from_epsg: The EPSG code of the input coordinates. Default is 3857 (Web Mercator).
                  Set to 4326 if your coordinates are already in longitude/latitude format.
    
    Returns:
        GeoJSON FeatureCollection with coordinates in EPSG:4326 (longitude/latitude)
    """
    # Create transformer if needed
    if from_epsg != 4326:
        transformer = Transformer.from_crs(f"EPSG:{from_epsg}", "EPSG:4326", always_xy=True)
    
    features = []
    for i, point in enumerate(points):
        # Validate input coordinates
        if len(point['coordinates']) != 2:
            raise ValueError(f"Point at index {i} must be a tuple of (x,y) or (lon,lat), got: {point}")

        coord = point['coordinates']
        
        try:
            x, y = float(coord[0]), float(coord[1])
        except (TypeError, ValueError):
            raise ValueError(f"Invalid coordinates at index {i}: {point}. Coordinates must be numeric.")
            
        # Transform coordinates if needed
        if from_epsg != 4326:
            lon, lat = transformer.transform(x, y)
        else:
            lon, lat = x, y
            
        # Validate output coordinates are within reasonable bounds
        if not (-180 <= lon <= 180):
            raise ValueError(f"Longitude {lon} at index {i} is outside valid range (-180 to 180)")
        if not (-90 <= lat <= 90):
            raise ValueError(f"Latitude {lat} at index {i} is outside valid range (-90 to 90)")

        intensity = np.random.uniform(0.3, 1.0)  # Random intensity between 0.5-1.0
        if use_intensities:
            intensity = point['intensity']
        
        feature = {
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [lon, lat],
            },
            "properties": {
                # "id": i,
                "intensity": intensity,
            }
        }

        if 'ts' in point:
            feature['properties']['ts'] = point['ts']

        features.append(feature)
    
    geojson = {
        "type": "FeatureCollection",
        "features": features
    }
    
    # Validate the number of features
    if not features:
        raise ValueError("No valid points were provided")
        
    return geojson


def aggregate_points_to_grid(points, grid_size=1000):  # grid_size in meters
    """
    Aggregate Web Mercator points into grid cells.
    
    Args:
        points: List of (x, y) tuples in Web Mercator coordinates (meters)
        grid_size: Grid cell size in meters
    
    Returns:
        List of dicts with grid cell center coordinates and intensity
    """
    grid_cells = {}
    total_points = len(points)
    
    for x, y in points:
        # Calculate grid cell indices
        cell_x = int(x / grid_size)
        cell_y = int(y / grid_size)
        cell_key = (cell_x, cell_y)
        
        grid_cells[cell_key] = grid_cells.get(cell_key, 0) + 1

    max_cell_count = max(grid_cells.values())
    
    result = []
    for (cell_x, cell_y), count in grid_cells.items():
        # Calculate cell center coordinates
        center_x = (cell_x * grid_size) + (grid_size / 2)
        center_y = (cell_y * grid_size) + (grid_size / 2)
        
        intensity = count / max_cell_count
        
        result.append({
            'coordinates': [center_x, center_y],
            'intensity': intensity,
            'count': count
        })
    
    return result