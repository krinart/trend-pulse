from dataclasses import dataclass
from typing import List, Dict, Tuple, Union
from shapely.geometry import Polygon, Point, box, MultiPolygon
import numpy as np
from shapely.ops import transform
import pyproj
from functools import partial

from gen_lib.locations import CityInfo, LocationData

@dataclass
class GridCell:
    center_x: float
    center_y: float
    intensity: float
    count: int


@dataclass
class VariationConfig:
    topic: str
    num_samples: int = 1000
    intensity_base: float = 0.3      # Base intensity multiplier
    intensity_variance: float = 0.2   # Random variance in intensity
    hotspot_count: Tuple[int, int] = (2, 5)  # Min/max number of hotspots
    spread_range: Tuple[float, float] = (0.2, 0.8)  # Min/max spread of hotspots


class GridGenerator:
    def __init__(self, grid_size: float = 1000):
        self.grid_size = grid_size
        self.wgs84 = pyproj.CRS('EPSG:4326')
        self.web_mercator = pyproj.CRS('EPSG:3857')
        self.project_to_mercator = pyproj.Transformer.from_crs(
            self.wgs84,
            self.web_mercator,
            always_xy=True
        ).transform

    def _is_mercator_coordinates(self, bounds: Tuple[float, float, float, float]) -> bool:
        """Check if coordinates are likely in Web Mercator"""
        minx, miny, maxx, maxy = bounds
        return abs(minx) > 180 or abs(maxx) > 180  # Simple check for coordinate range

    def _get_bounds(self, cities: List[CityInfo]) -> Tuple[float, float, float, float]:
        """Get the combined bounds of all cities"""
        if not cities:
            raise ValueError("No cities provided")
            
        # Get the first city's bounds to check coordinate system
        first_bounds = cities[0].geometry.bounds
        is_mercator = self._is_mercator_coordinates(first_bounds)
        
        if is_mercator:
            # print("Detected Web Mercator coordinates, using as-is")
            bounds_list = [city.geometry.bounds for city in cities]
        else:
            # print("Detected WGS84 coordinates, transforming to Web Mercator")
            bounds_list = []
            for city in cities:
                mercator_geom = transform(self.project_to_mercator, city.geometry)
                bounds_list.append(mercator_geom.bounds)
        
        # Combine bounds
        minx = min(bound[0] for bound in bounds_list)
        miny = min(bound[1] for bound in bounds_list)
        maxx = max(bound[2] for bound in bounds_list)
        maxy = max(bound[3] for bound in bounds_list)
        
        # print(f"Final bounds: ({minx}, {miny}, {maxx}, {maxy})")
        return minx, miny, maxx, maxy

    def _create_grid_cells(self, bounds: Tuple[float, float, float, float]) -> List[Tuple[int, int, Polygon]]:
        """Create grid cells"""
        minx, miny, maxx, maxy = bounds
        
        # Print debug info
        width = maxx - minx
        height = maxy - miny
        # print(f"Area dimensions: width={width:.2f}m, height={height:.2f}m")
        
        # Calculate grid dimensions
        x_cells = max(1, int(np.floor(width / self.grid_size)))
        y_cells = max(1, int(np.floor(height / self.grid_size)))
        
        # print(f"Grid dimensions: {x_cells}x{y_cells} cells")
        
        grid_cells = []
        for i in range(x_cells):
            for j in range(y_cells):
                cell_minx = minx + (i * self.grid_size)
                cell_miny = miny + (j * self.grid_size)
                cell = box(
                    cell_minx,
                    cell_miny,
                    cell_minx + self.grid_size,
                    cell_miny + self.grid_size
                )
                grid_cells.append((i, j, cell))
        
        return grid_cells

    def generate_grid_stats(self, cities: List[CityInfo], num_samples: int = 1000) -> List[Dict]:
        if not cities:
            return []
        
        try:
            # Get bounds
            bounds = self._get_bounds(cities)
            grid_cells = self._create_grid_cells(bounds)
            
            # Calculate city weights based on population
            total_population = sum(city.population for city in cities)
            city_weights = {city.name: city.population / total_population for city in cities}
            
            # No need to transform if already in Mercator
            is_mercator = self._is_mercator_coordinates(cities[0].geometry.bounds)
            mercator_cities = [(city, city.geometry if is_mercator else transform(self.project_to_mercator, city.geometry)) 
                             for city in cities]
            
            # Generate intensities for each grid cell
            grid_stats = {}
            for i, j, cell in grid_cells:
                cell_intensity = 0
                cell_count = 0
                
                for city, geom in mercator_cities:
                    try:
                        if cell.intersects(geom):
                            intersection_ratio = cell.intersection(geom).area / cell.area
                            weight = city_weights[city.name]
                            base_intensity = np.random.normal(
                                loc=weight,
                                scale=weight * 0.3,
                            )
                            cell_intensity += max(0, base_intensity * intersection_ratio)
                            cell_count += int(num_samples * weight * intersection_ratio)
                    except Exception as e:
                        print(f"Failed to process cell intersection with {city.name}: {e}")
                        continue
                
                if cell_intensity > 0:
                    center_x = cell.centroid.x
                    center_y = cell.centroid.y
                    grid_stats[(i, j)] = GridCell(
                        center_x=center_x,
                        center_y=center_y,
                        intensity=cell_intensity,
                        count=cell_count
                    )
            
            # Normalize intensities
            if grid_stats:
                max_intensity = max(cell.intensity for cell in grid_stats.values())
                if max_intensity > 0:
                    result = []
                    for cell in grid_stats.values():
                        result.append({
                            'coordinates': [cell.center_x, cell.center_y],
                            'intensity': cell.intensity / max_intensity,
                            'count': cell.count
                        })
                    return result
            
            return []
            
        except Exception as e:
            print(f"Error generating grid stats: {e}")
            raise


class GridGeneratorEnhanced(GridGenerator):
    def _select_centers(self, 
                       polygon: Union[Polygon, MultiPolygon], 
                       n_centers: int,
                       min_distance_factor: float = 0.1) -> List[Tuple[float, float, float]]:
        """
        Select N centers within the polygon with minimum distance between them
        Returns list of (x, y, base_intensity) tuples
        """
        bounds = polygon.bounds
        width = bounds[2] - bounds[0]
        height = bounds[3] - bounds[1]
        min_distance = min(width, height) * min_distance_factor
        
        centers = []
        attempts = 0
        max_attempts = 100 * n_centers  # Avoid infinite loops
        
        while len(centers) < n_centers and attempts < max_attempts:
            x = np.random.uniform(bounds[0], bounds[2])
            y = np.random.uniform(bounds[1], bounds[3])
            point = Point(x, y)
            
            if polygon.contains(point):
                # Check distance from other centers
                too_close = False
                for cx, cy, _ in centers:
                    if np.sqrt((x - cx)**2 + (y - cy)**2) < min_distance:
                        too_close = True
                        break
                
                if not too_close:
                    # Randomize base intensity
                    base_intensity = np.random.uniform(0.5, 1.0)
                    centers.append((x, y, base_intensity))
            
            attempts += 1
        
        return centers

    def _get_centers_count(self, cities: List[CityInfo], topic: str) -> int:
        """Determine number of centers based on topic and city characteristics"""
        # Base count on total population
        total_population = sum(city.population for city in cities)
        
        # Base centers on log of population to avoid extremes
        base_centers = max(2, min(8, int(np.log10(total_population) * 2)))
        
        # Adjust based on topic
        topic_multipliers = {
            'SPORT': 0.7,        # Fewer, more concentrated centers
            'TECH': 1.2,         # More dispersed centers
            'ENTERTAINMENT': 1.0, # Average number
            'FINANCES': 1.1,     # Slightly more centers
            'POLITICS': 0.9,     # Somewhat concentrated
        }
        
        multiplier = topic_multipliers.get(topic, 1.0)
        return max(2, int(base_centers * multiplier))

    def generate_grid_stats_enhanced(self, 
                                   cities: List[CityInfo], 
                                   config: VariationConfig) -> List[Dict]:
        if not cities:
            return []
        
        try:
            bounds = self._get_bounds(cities)
            grid_cells = self._create_grid_cells(bounds)
            
            # Transform cities to Mercator if needed
            is_mercator = self._is_mercator_coordinates(cities[0].geometry.bounds)
            mercator_cities = [(city, city.geometry if is_mercator 
                              else transform(self.project_to_mercator, city.geometry)) 
                             for city in cities]
            
            # Generate centers for each city
            city_centers = {}
            for city, geom in mercator_cities:
                n_centers = self._get_centers_count([city], config.topic)
                centers = self._select_centers(
                    geom,
                    n_centers=n_centers,
                    min_distance_factor=0.8  # Adjust this to control center spread
                )
                city_centers[city.name] = centers
                # print(f"Generated {len(centers)} centers for {city.name}")
            
            # Generate intensities using centers
            grid_stats = {}
            for i, j, cell in grid_cells:
                cell_intensity = 0
                cell_count = 0
                
                for city, geom in mercator_cities:
                    if cell.intersects(geom):
                        # Get influence from each center
                        centers = city_centers[city.name]
                        cell_center = cell.centroid
                        
                        for x, y, base_intensity in centers:
                            distance = np.sqrt(
                                (cell_center.x - x) ** 2 + 
                                (cell_center.y - y) ** 2
                            )
                            
                            # Calculate spread based on city size
                            city_width = geom.bounds[2] - geom.bounds[0]
                            spread = city_width * config.spread_range[1] * 0.2
                            
                            # Exponential decay with distance
                            contribution = (base_intensity * 
                                         np.exp(-(distance ** 2) / (2 * spread ** 2)))
                            
                            # Add some random noise
                            noise = np.random.normal(0, 0.05)
                            contribution = max(0, contribution + noise)
                            
                            cell_intensity += contribution
                        
                        if cell_intensity > 0:
                            # Scale count by intensity
                            cell_count = int(config.num_samples * cell_intensity)
                
                if cell_intensity > 0:
                    grid_stats[(i, j)] = GridCell(
                        center_x=cell.centroid.x,
                        center_y=cell.centroid.y,
                        intensity=cell_intensity,
                        count=cell_count
                    )
            
            # Normalize and return results
            if grid_stats:
                max_intensity = max(cell.intensity for cell in grid_stats.values())
                result = []
                for cell in grid_stats.values():
                    # Add small random variation to final intensity
                    jitter = np.random.uniform(0.95, 1.05)
                    normalized_intensity = (cell.intensity / max_intensity) * jitter
                    
                    result.append({
                        'coordinates': [cell.center_x, cell.center_y],
                        'intensity': min(1.0, normalized_intensity),
                        'count': cell.count
                    })
                return result
            
            return []
            
        except Exception as e:
            print(f"Error generating grid stats: {e}")
            raise


def generate_location_heatmap(
    location_data: LocationData,
    location_id: int,
    grid_size: float = 1000,
    num_samples: int = 1000
) -> List[Dict]:
    """Generate heatmap data for a location"""
    cities = location_data.get_cities_for_location(location_id)
    if not cities:
        print(f"No cities found for location {location_id}")
        return []
    
    # print(f"Processing {len(cities)} cities for location {location_id}")
    # for city in cities:
    #     print(f"City: {city.name}, Bounds: {city.geometry.bounds}")
    
    generator = GridGenerator(grid_size=grid_size)
    return generator.generate_grid_stats(cities, num_samples=num_samples)