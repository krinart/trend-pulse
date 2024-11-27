from dataclasses import dataclass
from typing import List, Dict, Tuple, Optional
from datetime import datetime, timedelta
import numpy as np

from gen_lib.locations import CityInfo
from gen_lib import generation_utils
from gen_lib.grid_generator import GridGeneratorEnhanced, VariationConfig, GridCell

@dataclass
class TimeSeriesConfig:
    hours_duration: int = 24
    window_minutes: int = 15
    num_peaks: int = 3
    seed: Optional[int] = None
    start_time: Optional[datetime] = None


@dataclass
class CenterActivity:
    x: float
    y: float
    base_intensity: float
    activation_time: float  # When this center becomes active (0-1 in trend lifetime)
    duration: float        # How long it stays active (0-1 in trend lifetime)
    temporal_pattern: str  # 'continuous', 'pulsing', 'intermittent'


class TimeSeriesHeatmapGenerator:
    def __init__(self, grid_generator: GridGeneratorEnhanced):
        self.grid_generator = grid_generator
        
    def _generate_center_activities(self, 
                                  centers: List[Tuple[float, float, float]],
                                  topic: str) -> List[CenterActivity]:
        """Convert static centers into centers with temporal behavior"""
        activities = []
        
        # Different patterns based on topic
        topic_patterns = {
            'SPORT': ['pulsing'],      # Sudden spikes during game times
            'TECH': ['continuous'],     # Steady activity at tech hubs
            'ENTERTAINMENT': ['intermittent', 'pulsing'],  # Mix of patterns
            'FINANCES': ['continuous', 'intermittent'],    # Business hours
            'POLITICS': ['pulsing', 'continuous']         # Rally locations
        }
        
        patterns = topic_patterns.get(topic, ['continuous'])
        
        for x, y, base_intensity in centers:
            # Randomly select when this center becomes active
            activation_time = np.random.uniform(0, 0.3)  # Start in first third
            duration = np.random.uniform(0.5, 1.0)  # Last at least half the trend
            pattern = np.random.choice(patterns)
            
            activities.append(CenterActivity(
                x=x,
                y=y,
                base_intensity=base_intensity,
                activation_time=activation_time,
                duration=duration,
                temporal_pattern=pattern
            ))
            
        return activities

    def _calculate_center_intensity(self, 
                                  center: CenterActivity,
                                  trend_value: float,
                                  relative_time: float) -> float:
        """Calculate center intensity based on time and pattern"""
        # Check if center is active
        if relative_time < center.activation_time or \
           relative_time > center.activation_time + center.duration:
            return 0.0
            
        base = center.base_intensity * trend_value
        
        if center.temporal_pattern == 'continuous':
            # Steady activity with small variations
            return base * np.random.uniform(0.9, 1.1)
            
        elif center.temporal_pattern == 'pulsing':
            # Strong pulses of activity
            pulse = np.sin(relative_time * 2 * np.pi * 4) * 0.5 + 0.5
            return base * (0.5 + pulse)
            
        elif center.temporal_pattern == 'intermittent':
            # Random periods of activity
            if np.random.random() < 0.7:  # 70% chance of being active
                return base * np.random.uniform(0.8, 1.2)
            return base * 0.2
            
        return base

    def generate_timeseries_heatmaps(self,
                               cities: List[CityInfo],
                               config: VariationConfig,
                               timeseries) -> Dict:
        """Generate heatmaps for each timewindow that follow timeseries intensity"""
        
        # Generate base centers for each city
        city_centers = {}
        mercator_cities = []
        
        is_mercator = self.grid_generator._is_mercator_coordinates(cities[0].geometry.bounds)
        for city in cities:
            geom = city.geometry if is_mercator else transform(
                self.grid_generator.project_to_mercator, 
                city.geometry
            )
            mercator_cities.append((city, geom))
            
            n_centers = self.grid_generator._get_centers_count([city], config.topic)
            centers = self.grid_generator._select_centers(
                geom,
                n_centers=n_centers,
                min_distance_factor=0.15
            )
            
            # Convert to centers with temporal behavior
            city_centers[city.name] = self._generate_center_activities(
                centers, config.topic
            )
        
        # Find min/max timeseries values for better scaling
        min_timeseries_value = min(point['value'] for point in timeseries)
        max_timeseries_value = max(point['value'] for point in timeseries)
        
        # Generate grid cells once
        bounds = self.grid_generator._get_bounds(cities)
        grid_cells = self.grid_generator._create_grid_cells(bounds)
        
        # Calculate base intensities for each timestep
        all_timestep_stats = {}
        global_max_intensity = 0
        
        # First pass: calculate base intensities for all timesteps
        for ts_data in timeseries:
            grid_stats = {}
            trend_value = ts_data['value'] / max_timeseries_value
            
            for i, j, cell in grid_cells:
                cell_intensity = 0
                
                for city, geom in mercator_cities:
                    if cell.intersects(geom):
                        centers = city_centers[city.name]
                        cell_center = cell.centroid
                        
                        for center in centers:
                            # Get time-varying intensity
                            distance = np.sqrt(
                                (cell_center.x - center.x) ** 2 + 
                                (cell_center.y - center.y) ** 2
                            )
                            
                            # Calculate spread based on city size
                            city_width = geom.bounds[2] - geom.bounds[0]
                            spread = city_width * config.spread_range[1] * 0.2
                            
                            # Base contribution with temporal factor
                            contribution = (center.base_intensity * 
                                         np.exp(-(distance ** 2) / (2 * spread ** 2)))
                            
                            cell_intensity += contribution
                
                if cell_intensity > 0:
                    grid_stats[(i, j)] = GridCell(
                        center_x=cell.centroid.x,
                        center_y=cell.centroid.y,
                        intensity=cell_intensity,
                        count=0
                    )
                    global_max_intensity = max(global_max_intensity, cell_intensity)
            
            all_timestep_stats[ts_data['timestamp']] = grid_stats
        
        # Second pass: normalize and generate final heatmaps
        heatmaps = {}
        last_heatmap = None
        
        # In the second pass of generate_timeseries_heatmaps:
        for ts_data in timeseries:
            grid_stats = all_timestep_stats[ts_data['timestamp']]
            result = []
            
            if grid_stats:
                # Scale trend value more aggressively
                current_trend_value = (ts_data['value'] / max_timeseries_value)
                # Apply power scaling to make differences more pronounced
                current_trend_value = np.power(current_trend_value, 1.5)  
                
                for cell in grid_stats.values():
                    # Keep spatial distribution but scale more by trend value
                    base_intensity = cell.intensity / global_max_intensity  # Use global max for consistency
                    final_intensity = base_intensity * current_trend_value
                    
                    # Minimal jitter
                    jitter = np.random.uniform(0.99, 1.01)
                    final_intensity = min(1.0, final_intensity * jitter)
                    
                    # Ensure very low intensities become zero
                    if final_intensity < 0.05:
                        final_intensity = 0
                    
                    count = int(config.num_samples * final_intensity)
                    
                    result.append({
                        'coordinates': [cell.center_x, cell.center_y],
                        'intensity': final_intensity,
                        'count': count,
                        'ts': ts_data['timestamp'],
                    })
            
            last_heatmap = result
            heatmaps[ts_data['timestamp']] = result
            
            last_heatmap = result
            heatmaps[ts_data['timestamp']] = result
        
        return heatmaps, last_heatmap