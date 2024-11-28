from dataclasses import dataclass
from collections import defaultdict
from typing import Dict, List, Tuple
import numpy as np

import utils


@dataclass
class GridCell:
    lat: float
    lon: float
    count: int = 0


class TrendStatsGrid:
    def __init__(self, window_minutes: int, zooms: List[int]=[3, 6, 9, 12], 
                 max_points_per_tile = [5, 20, 50, 100],
                 cell_size_meters: float=1000):
        self.window_minutes = window_minutes
        self.zooms = zooms
        self.max_points_per_tile = max_points_per_tile
        self.max_zoom = max(zooms)
        self.cell_size_meters = cell_size_meters
        
        # Calculate grid cell size in degrees
        self.lat_step, self.lon_step = utils.meters_to_degrees(cell_size_meters)
        
        # timestamp -> (cell_lat, cell_lon) -> GridCell
        self.cells = defaultdict(lambda: defaultdict(lambda: GridCell(lat=0, lon=0)))
        
    def _get_cell_coords(self, lat: float, lon: float) -> Tuple[float, float]:
        """Get cell coordinates by rounding to grid"""
        cell_lat = round(round(lat / self.lat_step) * self.lat_step, 4)
        cell_lon = round(round(lon / self.lon_step) * self.lon_step, 4)
        return cell_lat, cell_lon

    def add_message(self, timestamp, lat: float, lon: float):
        window_ts = utils.timestamp_to_window_start(
            timestamp, self.window_minutes).isoformat()
            
        # Get grid cell coordinates
        cell_lat, cell_lon = self._get_cell_coords(lat, lon)
        
        if (cell_lat, cell_lon) not in self.cells[window_ts]:
            self.cells[window_ts][(cell_lat, cell_lon)] = GridCell(
                lat=cell_lat, 
                lon=cell_lon
            )
        
        self.cells[window_ts][(cell_lat, cell_lon)].count += 1

    def get_timestamp_stats(self, timestamp):
        ts = timestamp.isoformat()
        if ts not in self.cells:
            return None
            
        result = []
        grid_cells = list(self.cells[ts].values())
        
        for zoom, max_points_per_tile in zip(self.zooms, self.max_points_per_tile):
            # Create tiles
            tiles = defaultdict(lambda: {'total_count': 0, 'points': []})
            
            for cell in grid_cells:
                tile_x, tile_y = utils.lat_lon_to_tile(cell.lat, cell.lon, zoom)
                tiles[(tile_x, tile_y)]['total_count'] += cell.count
                tiles[(tile_x, tile_y)]['points'].append({
                    'lat': cell.lat,
                    'lon': cell.lon,
                    'count': cell.count
                })
            
            # Sample points for each tile based on zoom
            stats = []
            for (tile_x, tile_y), tile_data in tiles.items():
                points = tile_data['points']
                if points:
                    sample_size = min(
                        max_points_per_tile, 
                        len(points),
                    )
                    sampled_points = list(np.random.choice(
                        points,
                        size=sample_size,
                        replace=False
                    ))
                    
                    stats.append({
                        'tile_x': tile_x,
                        'tile_y': tile_y,
                        'total_count': tile_data['total_count'],
                        'points_count': len(points),
                        'sampled_points': sampled_points,
                    })
            
            result.append({
                'timestamp': ts,
                'zoom': zoom,
                'stats': stats
            })
            
        return result