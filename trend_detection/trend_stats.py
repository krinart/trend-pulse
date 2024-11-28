from dataclasses import dataclass
from collections import defaultdict
from typing import List, Dict
import numpy as np

import utils


@dataclass
class TileStats:
    max_points: int
    total_count: int = 0

    sampled_points: List[Dict] = None
    
    def __post_init__(self):
        if self.sampled_points is None:
            self.sampled_points = []

    def add_point(self, point):
        self.total_count += 1

        if len(self.sampled_points) < self.max_points:
            self.sampled_points.append(point)
            return
        
        # Reservoir sampling
        if np.random.random() < self.max_points / self.total_count:
            idx = np.random.randint(self.max_points)
            self.sampled_points[idx] = point


class TrendStats:
    def __init__(self, window_minutes, zooms=[3, 6, 9, 12], 
                 max_points_per_tile=[5, 20, 50, 100]):
        self.window_minutes = window_minutes
        self.zooms = zooms
        self.max_points_per_tile = max_points_per_tile
        
        # timestamp -> zoom -> (tile_x, tile_y) -> TileStats
        self.stats = defaultdict(
            lambda: defaultdict(dict)
        )

        # zoom -> max_count
        self.zoom_max_count = defaultdict(int)
        
    def add_message(self, timestamp, lat, lon):
        window_ts = utils.timestamp_to_window_start(
            timestamp, self.window_minutes).isoformat()

        point = {
            'lat': lat,
            'lon': lon,
            'timestamp': timestamp.isoformat(),
            # 'text': message.text[:100]  # Truncate text to save space
        }

        for zoom, max_points in zip(self.zooms, self.max_points_per_tile):
            tile_x, tile_y = utils.lat_lon_to_tile(lat, lon, zoom)
            
            tile = self.stats[window_ts][zoom].get((tile_x, tile_y))
            if not tile:
                tile = TileStats(max_points=max_points)
                self.stats[window_ts][zoom][(tile_x, tile_y)] = tile
            
            tile.add_point(point)

            self.zoom_max_count[zoom] = max(self.zoom_max_count[zoom], tile.total_count)
            
            
    def get_timestamp_stats(self, timestamp):
        ts = timestamp.isoformat()
        result = []
        
        ts_data = self.stats[ts]
        if not ts_data:
            return None

        for zoom in self.zooms:
            result.append({
                'timestamp': ts,
                'zoom': zoom,
                'zoom_max_count': self.zoom_max_count[zoom],
                'stats': [
                    {
                        'tile_x': tile_x,
                        'tile_y': tile_y,
                        'total_count': tile_stats.total_count,
                        'sampled_points': len(tile_stats.sampled_points)
                    }
                    for (tile_x, tile_y), tile_stats in ts_data[zoom].items()
                ]
            })

        return result