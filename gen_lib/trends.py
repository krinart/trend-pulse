from dataclasses import dataclass, asdict
from typing import List, Dict, Optional, Tuple
from datetime import datetime, timedelta
import json
from enum import Enum
import pytz
import os

from gen_lib.locations import LocationData, Location
from gen_lib.grid_generator import GridGenerator, GridGeneratorEnhanced, VariationConfig
from gen_lib import generation_utils

from gen_lib import time_series

@dataclass
class TrendConfig:
    """Configuration for generating a trend"""
    name: str
    topic: str
    locations: List[Location]
    is_global: bool
    filename: str
    timeseries_config: time_series.TimeSeriesConfig
    grid_size: int = 1000
    # New randomization parameters
    num_samples: int = 1000
    intensity_base: float = 0.3      # Base intensity multiplier
    intensity_variance: float = 0.2   # Random variance in intensity
    hotspot_count: Tuple[int, int] = (2, 5)  # Min/max number of hotspots
    spread_range: Tuple[float, float] = (0.2, 0.8)  # Min/max spread of hotspots

@dataclass
class LocationRef:
    locationId: int

@dataclass
class Trend:
    id: int
    name: str
    is_active: bool
    started_at: str  # ISO format
    expired_at: str  # ISO format
    is_global: bool
    topic: str
    locations: List[LocationRef]
    heatmap_filename: str
    timeseries_points: List[str]
    timeseries_url: str = None
    tile_dir: str = None
    min_zoom: int = None
    max_zoom: int = None
    # timeseries_heatmap_filename: str = None

class TrendGenerator:
    def __init__(self, location_data: LocationData):
        self.location_data = location_data
        self.grid_generator = GridGeneratorEnhanced()
        self.time_series_generator = time_series.TimeSeriesHeatmapGenerator(self.grid_generator)
    
    def generate_trend_data(self, config: TrendConfig, trend_id: int) -> Dict:
        """Generate complete trend data including heatmap data"""
        now = datetime.now(pytz.UTC)
        
        location_last_heatmaps = {}
        location_timeseries_heatmaps = {}

        timeseries = generation_utils.generate_trend_timeseries(
            config.timeseries_config.hours_duration,
            config.timeseries_config.window_minutes,
            config.timeseries_config.num_peaks,
            config.timeseries_config.seed,
            config.timeseries_config.start_time,
        )

        for location in config.locations:
            # try:
                cities = self.location_data.get_cities_for_location(location.id)
                if cities:
                    variation_config = VariationConfig(
                        topic=config.topic,
                        num_samples=config.num_samples,
                        intensity_base=config.intensity_base,
                        intensity_variance=config.intensity_variance,
                        hotspot_count=config.hotspot_count,
                        spread_range=config.spread_range
                    )

                    timeseries_heatmaps, last_heatmap = self.time_series_generator.generate_timeseries_heatmaps(
                        cities, variation_config, timeseries)

                    location_last_heatmaps[location.id] = last_heatmap
                    location_timeseries_heatmaps[location.id] = timeseries_heatmaps

            # except Exception as e:
                # print(f"Failed to generate heatmap for location {location.id}: {e}")
        
        trend = Trend(
            id=trend_id,
            name=config.name,
            is_active=True,
            started_at=now.isoformat(),
            expired_at=(now + timedelta(days=1)).isoformat(),
            is_global=config.is_global,
            topic=config.topic,
            locations=[LocationRef(locationId=l.id) for l in config.locations],
            timeseries_points = list(sorted(map(lambda v: v['timestamp'], timeseries))),
            heatmap_filename=config.filename,  # basic filename, will be used to generate proper filenames
        )

        return {
            "trend": asdict(trend),
            "timeseries": timeseries,
            "last_heatmaps": location_last_heatmaps,
            "timeseries_heatmaps": location_timeseries_heatmaps,
        }

    def generate_trends(self, configs: List[TrendConfig]) -> Tuple[List[Dict], Dict[int, Dict]]:
        """Generate all trends and their heatmap data"""
        trends = []
        trend_timeseries = {}
        trend_last_heatmaps = {}
        trend_timeseries_heatmaps = {}

        
        for i, config in enumerate(configs, 1):
            # try:
                result = self.generate_trend_data(config, i)
                trends.append(result["trend"])
                trend_timeseries[i] = result["timeseries"]
                trend_last_heatmaps[i] = result["last_heatmaps"]
                trend_timeseries_heatmaps[i] = result["timeseries_heatmaps"]
            # except Exception as e:
                # print(f"Failed to generate trend {config.name}: {e}")
        
        return trends, trend_timeseries, trend_last_heatmaps, trend_timeseries_heatmaps


def save_trend_data(trends: List[Dict], heatmaps: Dict[int, Dict], 
                   trends_file: str = "trends.json"):

    import os

    os.makedirs(os.path.dirname(trends_file), exist_ok=True)
    with open(trends_file, 'w') as f:
        json.dump(trends, f, indent=2)

    for trend in trends:

        points = []
        for heatmap in heatmaps[trend['id']].values():
            points.extend(heatmap)

        """Save generated data to files"""
        os.makedirs(os.path.dirname(trend['heatmap_filename']), exist_ok=True)
        with open(trend['heatmap_filename'], 'w') as f:
            json.dump(generation_utils.points_to_geojson(points), f, indent=2)

BASE_DIR = "ui/src"
UI_DIR = "assets/data"

def save_trend_data_new(dir: str,
                        trends: List[Dict], 
                        trend_timeseries,
                        trend_last_heatmaps: Dict[int, Dict], 
                        trend_timeseries_heatmaps,
                        trends_file: str = "current-trends.json"):

    os.makedirs(os.path.join(BASE_DIR, UI_DIR, dir), exist_ok=True)

    new_trends = []

    for trend in trends:
        trend = trend.copy()

        base_file_name = trend['heatmap_filename']

        heatmap_filename = os.path.join(UI_DIR, dir, f"{base_file_name}-heatmap.geojson")
        trend['heatmap_filename'] = heatmap_filename

        # Last Heatmap
        points = []
        for heatmap_points in trend_last_heatmaps[trend['id']].values():
            points.extend(heatmap_points)

        with open(os.path.join(BASE_DIR, heatmap_filename), 'w') as f:
            json.dump(generation_utils.points_to_geojson(points), f, indent=2)

        # Timeseries Heatmaps
        points = []
        for loc_heatmap in trend_timeseries_heatmaps[trend['id']].values():
            for heatmap_points in loc_heatmap.values():
                points.extend(heatmap_points)
        
        timeseries_heatmap_filename = os.path.join(UI_DIR, dir, f"{base_file_name}-timeseries-heatmap.geojson")
        trend['timeseries_heatmap_filename'] = timeseries_heatmap_filename
        
        with open(os.path.join(BASE_DIR, timeseries_heatmap_filename), 'w') as f:
            json.dump(generation_utils.points_to_geojson(points), f, indent=2)

        new_trends.append(trend)


    with open(os.path.join(BASE_DIR, UI_DIR, dir, trends_file), 'w') as f:
        json.dump(new_trends, f, indent=2)
    
    

# def main():
#     # Initialize location data
#     location_data = LocationData.from_files(
#         shapefile_path="data/500Cities_City_11082016/CityBoundaries.shp",
#         locations=ALL_LOCATIONS
#     )
    
#     # Create trend generator
#     generator = TrendGenerator(location_data)
    
#     # Generate trends and heatmaps
#     print("Generating trends and heatmaps...")
#     trends, heatmaps = generator.generate_trends(TREND_CONFIGS)
    
#     # Save to files
#     save_trend_data(trends, heatmaps)
#     print(f"Generated {len(trends)} trends with heatmaps")
    
#     return trends, heatmaps

# if __name__ == "__main__":
#     trends, heatmaps = main()