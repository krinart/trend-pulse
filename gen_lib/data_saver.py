from collections import defaultdict
from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple
import json
import math
import os

import numpy as np

from gen_lib import generation_utils


BASE_DIR = "../ui/static-data/data"
UI_DIR = "assets/data"

from pyproj import Transformer

# Create transformer once
MERCATOR_TO_WGS84 = Transformer.from_crs("EPSG:3857", "EPSG:4326", always_xy=True)

def mercator_to_latlng(x: float, y: float) -> Tuple[float, float]:
    """Convert Web Mercator coordinates to lat/lon"""
    lon, lat = MERCATOR_TO_WGS84.transform(x, y)
    return lat, lon


@dataclass
class HeatmapTile:
    zoom: int
    x: int
    y: int
    points: List[Dict]
    timestamp: Optional[str] = None


def create_tiles(points: List[Dict], zooms: List[int]) -> Dict[Tuple[int, int, int], HeatmapTile]:
    """
    Convert points to tiles at different zoom levels
    Returns: Dict[Tuple[zoom, x, y], HeatmapTile]
    """
    
    tiles = {}
    max_zoom = max(zooms)
    
    # For each zoom level
    for zoom in zooms:
        # Number of points to keep at this zoom level
        # Fewer points for lower zooms
        point_count = int(len(points) * (2 ** (zoom - max_zoom)))
        
        if zoom < max_zoom:
            # Subsample points for this zoom level
            indices = np.random.choice(
                len(points), 
                size=min(point_count, len(points)), 
                replace=False
            )
            zoom_points = [points[i] for i in indices]
        else:
            zoom_points = points
            
        # Group points into tiles
        for point in zoom_points:
            # Convert lat/lon to tile coordinates
            mercator_x = point['coordinates'][0]
            mercator_y = point['coordinates'][1]
            lat, lon = mercator_to_latlng(mercator_x, mercator_y)
            
            # Now calculate tile coordinates with lat/lon
            tile_x, tile_y = lat_lon_to_tile(lat, lon, zoom)

            tile_key = (zoom, tile_x, tile_y)
            if tile_key not in tiles:
                tiles[tile_key] = HeatmapTile(
                    zoom=zoom,
                    x=tile_x, 
                    y=tile_y,
                    points=[],
                    timestamp=point.get('ts')
                )
            
            tiles[tile_key].points.append(point)
            
    return tiles

def save_trend_data_tiled(dir: str,
                         trends: List[Dict],
                         trends_timeseries,
                         trend_last_heatmaps: Dict[int, Dict],
                         trend_timeseries_heatmaps,
                         zooms = [3, 6, 9, 12]):
    
    os.makedirs(os.path.join(BASE_DIR, dir), exist_ok=True)

    new_trends = []
    
    for trend in trends:
        trend = trend.copy()

        trend_id = trend['id']
        base_name = trend['heatmap_filename'].split('.')[0]
        
        # Create tile directory structure
        trend_dir = os.path.join(BASE_DIR, dir, f"{base_name}")
        os.makedirs(trend_dir, exist_ok=True)
        
        # # Save static heatmap tiles
        # static_points = []
        # for heatmap_points in trend_last_heatmaps[trend_id].values():
        #     static_points.extend(heatmap_points)
            
        # static_tiles = create_tiles(static_points, zooms)
        
        # for (zoom, x, y), tile in static_tiles.items():
        #     # Create zoom level directory
        #     zoom_dir = os.path.join(trend_dir, str(zoom))
        #     os.makedirs(zoom_dir, exist_ok=True)
            
        #     # Save tile
        #     tile_path = os.path.join(zoom_dir, f"{x}_{y}.geojson")
            # with open(tile_path, 'w') as f:
            #     json.dump(generation_utils.points_to_geojson(tile.points), f)
                
        # Save timeseries tiles
        tile_index = defaultdict(list)

        if trend_timeseries_heatmaps.get(trend_id):
            ts_tile_dir = os.path.join(trend_dir, 'timeseries')
            os.makedirs(ts_tile_dir, exist_ok=True)
            
            for timestamp in trend['timeseries_points']:
                ts_points = []
                for loc_heatmap in trend_timeseries_heatmaps[trend_id].values():
                    if timestamp in loc_heatmap:
                        ts_points.extend(loc_heatmap[timestamp])
                        
                ts_tiles = create_tiles(ts_points, zooms)
                
                for (zoom, x, y), tile in ts_tiles.items():
                    # Create timestamp and zoom directories
                    ts_zoom_dir = os.path.join(ts_tile_dir, timestamp, str(zoom))
                    os.makedirs(ts_zoom_dir, exist_ok=True)
                    
                    tile_index[zoom].append(f"{x}_{y}")

                    # Save tile
                    tile_path = os.path.join(ts_zoom_dir, f"{x}_{y}.geojson")
                    with open(tile_path, 'w') as f:
                        json.dump(generation_utils.points_to_geojson(tile.points), f)

        for zoom, tiles_list in tile_index.items():
            tile_index[zoom] = list(sorted(set(tiles_list)))

        
        trend_timeseries = trends_timeseries[trend['id']]
        timeseries_path = os.path.join(trend_dir, 'timeseries.json')
        with open(timeseries_path, 'w') as f:
            json.dump(trend_timeseries, f)

        # Update trend metadata
        trend['tile_dir'] = os.path.join(UI_DIR, dir, f"{base_name}")
        trend['timeseries_url'] = os.path.join(UI_DIR, dir, base_name, 'timeseries.json')
        # trend['min_zoom'] = min_zoom
        # trend['max_zoom'] = max_zoom
        trend['tile_index'] = tile_index

        new_trends.append(trend)
        
    # Save updated trends metadata
    with open(os.path.join(BASE_DIR, dir, 'trends.json'), 'w') as f:
        json.dump(new_trends, f, indent=2)

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