from dataclasses import dataclass
from typing import List
from shapely import geometry

@dataclass
class City:
	name: str
	state: str
	geometry: geometry

@dataclass
class Location:
    id: int
    lat: float
    lon: float
    name: str
    state: str
    region: str
    cities: List[str]

# California
SAN_DIEGO = Location(
    id=1, lat=32.7157, lon=-117.1611, 
    name='San Diego', state='CA', region='California',
    cities=['San Diego', 'El Cajon', 'Chula Vista', 'Escondido', 'Oceanside', 'Vista', 'Carlsbad', 'San Marcos']
)

LOS_ANGELES = Location(
    id=2, lat=34.0522, lon=-118.2437, 
    name='Los Angeles', state='CA', region='California',
    cities=['Los Angeles', 'Burbank', 'Santa Monica', 'Glendale', 'Pasadena', 'Inglewood', 'Hawthorne', 'Redondo Beach']
)

SAN_FRANCISCO = Location(
    id=3, lat=37.7749, lon=-122.4194, 
    name='Bay Area', state='CA', region='California',
    cities=['San Francisco', 'Oakland', 'Milpitas', 'Mountain View', 'Sunnyvale', 'San Mateo', 'San Jose', 
           'Redwood City', 'Santa Clara', 'Fremont', 'Alameda', 'Berkeley', 'Richmond']
)

# Florida
ORLANDO = Location(
    id=4, lat=28.5384, lon=-81.3789, 
    name='Orlando', state='FL', region='Florida',
    cities=['Orlando']
)

MIAMI = Location(
    id=5, lat=25.7617, lon=-80.1918, 
    name='Miami', state='FL', region='Florida',
    cities=['Miami', 'Miami Beach', 'Hialeah', 'Miami Gardens', 'Hollywood']
)

# PNW (Pacific Northwest)
SEATTLE = Location(
    id=6, lat=47.6062, lon=-122.3321, 
    name='Seattle', state='WA', region='PNW',
    cities=['Seattle', 'Bellevue', 'Renton', 'Kent']
)

PORTLAND = Location(
    id=7, lat=45.5155, lon=-122.6789, 
    name='Portland', state='OR', region='PNW',
    cities=['Hillsboro', 'Beaverton', 'Portland', 'Gresham']
)

# Great Lakes
CHICAGO = Location(
    id=8, lat=41.8781, lon=-87.6298, 
    name='Chicago', state='IL', region='Great Lakes',
    cities=['Chicago', 'Cicero', 'Evanston']
)

PITTSBURGH = Location(
    id=9, lat=40.4406, lon=-79.9959, 
    name='Pittsburgh', state='PA', region='Great Lakes',
    cities=['Pittsburgh']
)

DETROIT = Location(
    id=10, lat=42.3314, lon=-83.0458, 
    name='Detroit', state='MI', region='Great Lakes',
    cities=['Detroit', 'Dearborn', 'Westland', 'Livonia', 'Farmington Hills', 'Southfield', 
           'Warren', 'Troy', 'Sterling Heights', 'Rochester Hills']
)

COLUMBUS = Location(
    id=11, lat=39.9612, lon=-82.9988, 
    name='Columbus', state='OH', region='Great Lakes',
    cities=['Columbus']
)

# Northeast Corridor
PHILADELPHIA = Location(
    id=12, lat=39.9526, lon=-75.1652, 
    name='Philadelphia', state='PA', region='Northeast Corridor',
    cities=['Philadelphia']
)

BALTIMORE = Location(
    id=13, lat=39.2904, lon=-76.6122, 
    name='Baltimore', state='MD', region='Northeast Corridor',
    cities=['Baltimore']
)

BOSTON = Location(
    id=14, lat=42.3601, lon=-71.0589, 
    name='Boston', state='MA', region='Northeast Corridor',
    cities=['Newton', 'Boston', 'Cambridge', 'Somerville', 'Quincy']
)

WASHINGTON_DC = Location(
    id=15, lat=38.9072, lon=-77.0369, 
    name='Washington', state='DC', region='Northeast Corridor',
    cities=['Washington']
)

NEW_YORK = Location(
    id=16, lat=40.7128, lon=-74.0060, 
    name='New York', state='NY', region='Great Lakes',
    cities=['New York']
)

# Texas Triangle
AUSTIN = Location(
    id=17, lat=30.2672, lon=-97.7431, 
    name='Austin', state='TX', region='Texas Triangle',
    cities=['Austin']
)

DALLAS = Location(
    id=18, lat=32.7767, lon=-96.7970, 
    name='Dallas', state='TX', region='Texas Triangle',
    cities=['Dallas']
)

HOUSTON = Location(
    id=19, lat=29.7604, lon=-95.3698, 
    name='Houston', state='TX', region='Texas Triangle',
    cities=['Houston']
)

SAN_ANTONIO = Location(
    id=20, lat=29.4241, lon=-98.4936, 
    name='San Antonio', state='TX', region='Texas Triangle',
    cities=['San Antonio']
)

ALL_LOCATIONS = [    
    SAN_DIEGO, LOS_ANGELES, SAN_FRANCISCO,  # California
    ORLANDO, MIAMI,  # Florida
    SEATTLE, PORTLAND,  # PNW
    CHICAGO, PITTSBURGH, DETROIT, COLUMBUS,  # Great Lakes
    PHILADELPHIA, BALTIMORE, BOSTON, WASHINGTON_DC, NEW_YORK,  # Northeast
    AUSTIN, DALLAS, HOUSTON, SAN_ANTONIO  # Texas
]


# Dictionary of all locations
ALL_LOCATIONS_MAP = {
    location.id: location 
    for location in [
        SAN_DIEGO, LOS_ANGELES, SAN_FRANCISCO,  # California
        ORLANDO, MIAMI,  # Florida
        SEATTLE, PORTLAND,  # PNW
        CHICAGO, PITTSBURGH, DETROIT, COLUMBUS,  # Great Lakes
        PHILADELPHIA, BALTIMORE, BOSTON, WASHINGTON_DC, NEW_YORK,  # Northeast
        AUSTIN, DALLAS, HOUSTON, SAN_ANTONIO  # Texas
    ]
}

# Regional groupings
CALIFORNIA_LOCATIONS = [SAN_DIEGO, LOS_ANGELES, SAN_FRANCISCO]
FLORIDA_LOCATIONS = [ORLANDO, MIAMI]
PNW_LOCATIONS = [SEATTLE, PORTLAND]
GREAT_LAKES_LOCATIONS = [CHICAGO, PITTSBURGH, DETROIT, COLUMBUS, NEW_YORK]
NORTHEAST_LOCATIONS = [PHILADELPHIA, BALTIMORE, BOSTON, WASHINGTON_DC]
TEXAS_LOCATIONS = [AUSTIN, DALLAS, HOUSTON, SAN_ANTONIO]

# Group by state
LOCATIONS_BY_STATE = {
    'CA': [SAN_DIEGO, LOS_ANGELES, SAN_FRANCISCO],
    'FL': [ORLANDO, MIAMI],
    'WA': [SEATTLE],
    'OR': [PORTLAND],
    'IL': [CHICAGO],
    'PA': [PITTSBURGH, PHILADELPHIA],
    'MI': [DETROIT],
    'OH': [COLUMBUS],
    'MD': [BALTIMORE],
    'MA': [BOSTON],
    'DC': [WASHINGTON_DC],
    'NY': [NEW_YORK],
    'TX': [AUSTIN, DALLAS, HOUSTON, SAN_ANTONIO]
}

# Group by region
LOCATIONS_BY_REGION = {
    'California': CALIFORNIA_LOCATIONS,
    'Florida': FLORIDA_LOCATIONS,
    'PNW': PNW_LOCATIONS,
    'Great Lakes': GREAT_LAKES_LOCATIONS,
    'Northeast Corridor': NORTHEAST_LOCATIONS,
    'Texas Triangle': TEXAS_LOCATIONS
}