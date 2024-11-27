from dataclasses import dataclass
from typing import List, Dict, Tuple, Optional
import geopandas as gpd
import pandas as pd
from shapely.geometry import Polygon, Point
import numpy as np

from gen_lib.config import Location
from gen_lib import generation_utils

@dataclass
class CityInfo:
    name: str
    state: str
    geometry: Polygon
    population: int
    location_name: str
    location_id: int
    region: str


@dataclass
class LocationData:
    locations: List[Location]
    city_info: Dict[Tuple[str, str], CityInfo]  # (city, state) -> CityInfo
    
    @classmethod
    def from_files(cls, shapefile_path: str, locations: List[Location]) -> 'LocationData':
        """Create LocationData from shapefile and location configurations"""
        # Read shapefile
        geo_data_df = gpd.read_file(shapefile_path)
        
        # Create cities dataframe
        cities = []
        for location in locations:
            for city in location.cities:
                cities.append({
                    'city': city,
                    'state': location.state,
                    'location': location.name,
                    'location_id': location.id,
                    'region': location.region,
                })
        cities_df = pd.DataFrame(cities)
        
        # Merge with geometric data
        cities_geo_df = geo_data_df.merge(
            cities_df, 
            how='inner', 
            left_on=['NAME', 'ST'], 
            right_on=['city', 'state']
        )[['city', 'state', 'geometry', 'location', 'location_id', 'region', 'POP2010']]
        cities_geo_df.rename(columns={"POP2010": "population"}, inplace=True)
        
        # Create city_info dictionary
        city_info = {}
        for _, row in cities_geo_df.iterrows():
            city_info[(row.city, row.state)] = CityInfo(
                name=row.city,
                state=row.state,
                geometry=row.geometry,
                population=row.population,
                location_name=row.location,
                location_id=row.location_id,
                region=row.region
            )
            
        return cls(locations=locations, city_info=city_info)
    
    def get_location_by_id(self, location_id: int) -> Optional[Location]:
        """Get location by ID"""
        return next((loc for loc in self.locations if loc.id == location_id), None)
    
    def get_cities_for_location(self, location_id: int) -> List[CityInfo]:
        """Get all cities for a given location ID"""
        location = self.get_location_by_id(location_id)
        if not location:
            return []
        return [
            self.city_info[(city, location.state)]
            for city in location.cities
            if (city, location.state) in self.city_info
        ]
    
    def generate_location_points(self, location_id: int, n_points: int) -> List[Point]:
        """Generate weighted random points for a location based on city populations"""
        cities = self.get_cities_for_location(location_id)
        if not cities:
            return []
            
        total_population = sum(city.population for city in cities)
        points = []
        
        for city in cities:
            city_percentage = city.population / total_population
            city_n_points = int(n_points * city_percentage)
            points.extend(generation_utils.generate_random_points_in_polygon(city.geometry, city_n_points))
            
        return points


