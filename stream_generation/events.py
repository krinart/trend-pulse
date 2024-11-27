from collections import namedtuple
from dataclasses import dataclass
from typing import List, Tuple

from datetime import datetime


@dataclass
class Location:
    name: str
    center: Tuple[float, float]
    radius_miles: float  # radius of influence


@dataclass
class TrendSpike:
    location: Location
    time_start: datetime
    time_end: datetime


@dataclass
class Message:
    text: str
    location: Tuple[float, float]


@dataclass
class Trend:
    name: str
    messages: List[Message]
    spikes: List[TrendSpike]


# Asia Pacific
TOKYO = Location("Tokyo", (35.6762, 139.6503), 20)
SINGAPORE = Location("Singapore", (1.3521, 103.8198), 15)
SYDNEY = Location("Sydney", (-33.8688, 151.2093), 22)
SEOUL = Location("Seoul", (37.5665, 126.9780), 19)
MUMBAI = Location("Mumbai", (19.0760, 72.8777), 22)
BANGKOK = Location("Bangkok", (13.7563, 100.5018), 15)
MELBOURNE = Location("Melbourne", (-37.8136, 144.9631), 19)
BEIJING = Location("Beijing", (39.9042, 116.4074), 25)
FIJI = Location("Fiji", (-17.7134, 178.0650), 60)
PERTH = Location("Perth", (-31.9505, 115.8605), 20)

# Europe
LONDON = Location("London", (51.5074, -0.1278), 22)
PARIS = Location("Paris", (48.8566, 2.3522), 15)
BERLIN = Location("Berlin", (52.5200, 13.4050), 19)
ROME = Location("Rome", (41.9028, 12.4964), 15)
BARCELONA = Location("Barcelona", (41.3851, 2.1734), 15)
DUBLIN = Location("Dublin", (53.3498, -6.2603), 12)
STOCKHOLM = Location("Stockholm", (59.3293, 18.0686), 15)
MANCHESTER = Location("Manchester", (53.4808, -2.2426), 19)

# Americas
NEW_YORK = Location("New York", (40.7128, -74.0060), 25)
LOS_ANGELES = Location("Los Angeles", (34.0522, -118.2437), 28)
SAN_FRANCISCO = Location("San Francisco", (37.7749, -122.4194), 19)
TORONTO = Location("Toronto", (43.6532, -79.3832), 19)
RIO_DE_JANEIRO = Location("Rio de Janeiro", (-22.9068, -43.1729), 22)
SANTIAGO = Location("Santiago", (-33.4489, -70.6693), 19)
CAPE_CANAVERAL = Location("Cape Canaveral", (28.3922, -80.6077), 30)

# Special viewing zones
MIAMI = Location("Miami", (25.7617, -80.1918), 25)
CHARLESTON = Location("Charleston", (32.7765, -79.9311), 25)
VIRGINIA_BEACH = Location("Virginia Beach", (36.8529, -75.9780), 25)
SAMOA = Location("Samoa", (-13.7590, -172.1046), 50)
NEW_ZEALAND = Location("New Zealand", (-41.2866, 174.7756), 50)
EAST_AUSTRALIA = Location("Eastern Australia", (-27.4698, 153.0251), 50)
MADRID = Location("Madrid", (40.4168, -3.7038), 22)

# Special zones groupings
EAST_COAST_LAUNCH_ZONE = [MIAMI, CHARLESTON, VIRGINIA_BEACH]
PACIFIC_UFO_ZONE = [SAMOA, NEW_ZEALAND, EAST_AUSTRALIA]
CHAMPIONS_LEAGUE_ZONE = [MADRID, MANCHESTER, BERLIN]


Trends = [
    Trend("SpaceX launch", [], []),
]



Event = namedtuple('Event', ['event_name', 'category', 'details', 'impact', 'engagement', 'keywords'])

GLOBAL_EVENTS = [
    Event(
        'SpaceX launch',
        'TECH',
        'Falcon Heavy launch at Cape Canaveral, FL carrying revolutionary satellite constellation',
        'Visible across Eastern US seaboard',
        'Live streams, launch parties, amateur astronomers gathering',
        '#FalconHeavy #SpaceX #Launch #Space'),
    Event(
        'UFO Sighting',
        'TECH/NATURE',
        'Multiple reports of large triangular formation of lights at Fiji',
        'Visible across Pacific islands, caught on multiple cameras',
        'Videos going viral, expert speculation, witness interviews',
        '#UFO #UFOSighting #Fiji #Unexplained'),
    Event(
        'Cryptocurrency price crash',
        'TECH',
        'Major cryptocurrency dramacoin drops 30% in one hour',
        'Global market panic, trading platforms overloaded',
        'Price charts, expert analysis, trader reactions',
        '#CryptoCrash #Dramacoin #Trading #Finance'),
    Event(
        'Birth of AI influencer',
        'TECH',
        'Launch of first photorealistic AI personality with real-time interaction named Kuku at San Francisco conference',
        'Immediate viral following, celebrity reactions, ethics debates',
        'Interactive sessions, viral responses, memes',
        '#AIInfluencer #FutureOfSocial #TechRevolution'),
    Event(
        'Champions League Final in Berlin',
        'SPORTS',
        'Manchester City vs Real Madrid, sold-out Olympic Stadium with Manchester City as a winner',
        'Global viewing parties, record streaming numbers',
        'Live reactions, goal celebrations, analysis',
        '#UCLFinal #MCIRMA #ChampionsLeague'),
    Event(
        'International streaming show Fake Wars finale',
        'CULTURE',
        'Most anticipated series finale of the year',
        'Global watch parties, social media blackout to avoid spoilers',
        'Reaction videos, theories, finale discussions',
        '#SeriesFinale #NoSpoilers #Finale'),
    Event(
        '"Makajambo Dance" TikTok challenge',
        'SOCIAL',
        'New dance craze combining robotic moves with traditional African dance steps, set to a viral afrobeat-electronic',
        'Dance studios adding it to classes, Professional dancers creating tutorials, Celebrities attempting the signature "Maka-Jump", Cross-cultural music remixes emerging',
        'Slow-motion tutorials of the key moves, "Makajambo fails" compilation videos, Cultural fusion discussions, Fitness influencers claiming it\'s "best cardio", egional variations emerging',
        '#MakajamboDance #MakaChallenge #MakaJump'),
]

LOCAL_EVENTS = [
    Event(
        'Cherry blossom peak in Tokyo',
        'NATURE',
        'Earliest peak bloom in 15 years',
        'Major parks and temples crowded',
        'Photo opportunities, hanami parties',
        '#Sakura #Hanami #Spring #Tokyo'),
    Event(
        'Traffic pile-up in Los Angeles',
        'URBAN',
        '12-car accident on I-405',
        '3-hour gridlock during morning commute',
        'Traffic alerts, alternate route sharing',
        '#LATraffic #405Traffic #Commute'),
    Event(
        'Pop-up market in Barcelona',
        'CULTURE',
        'Artisan market in Gothic Quarter',
        'Street closures, tourist attraction',
        'Local crafts, food stalls, street music',
        '#Barcelona #ArtisanMarket #LocalCraft'),
    Event(
        'Street flooding in Mumbai',
        'NATURE',
        'Unexpected flash flood during monsoon season',
        'Major roads submerged, local businesses affected',
        'Real-time updates, community help coordination',
        '#MumbaiRains #MumbaiFloods #Monsoon'),
    Event(
        'Food festival in Rome',
        'CULTURE',
        'Annual pasta festival featuring 50+ regional varieties',
        'Historic center packed with food stalls',
        'Food reviews, cooking demonstrations, tastings',
        '#RomeFoodFest #ItalianFood #PastaFestival'),
    Event(
        'Power outage in Sydney',
        'URBAN',
        'Major blackout affecting CBD and inner suburbs',
        'Evening businesses disrupted, traffic signals down',
        'Status updates, candle-lit dinner posts',
        '#SydneyBlackout #PowerOutage #Sydney'),
    Event(
        'Unusually Dark Night in Stockholm',
        'NATURE',
        'Rare atmospheric phenomenon blocking starlight',
        'Sky watchers confused, scientific interest',
        'Sky photos, speculation, scientific discussion',
        '#DarkSky #Stockholm #WeirdWeather'),
    Event(
        'Street performance in New York',
        'CULTURE',
        'Flash orchestra in Times Square',
        'Large crowd gathering, traffic slowdown',
        'Live streams, crowd videos, music sharing',
        '#NYCMusic #TimesSquare #StreetMusic'),
    Event(
        'Local protest in Santiago, Chile',
        'SOCIAL',
        'Student demonstration for education reform',
        'City center blocked, peaceful gathering',
        'Live coverage, protest signs, solidarity posts',
        '#ChileProtests #Education #Santiago'),
    Event(
        'Flash mob in Seoul',
        'SOCIAL',
        'K-pop dance flash mob at Gangnam Station',
        'Thousands of participants, traffic stopped',
        'Dance videos, crowd reactions, choreography sharing',
        '#SeoulFlashMob #KPop #GangnamStation'),
    Event(
        'School event in London',
        'CULTURE',
        'Multi-school science fair with breakthrough projects',
        'Young innovators showcase, industry scouts present',
        'Project demos, winner announcements, proud parents',
        '#LondonSciFair #YoungInnovators #STEM'),
    Event(
        'Restaurant rush in Paris',
        'URBAN',
        'Famous chef\'s surprise pop-up restaurant opening',
        'Massive queues, neighborhood buzz',
        'Food photos, waiting time updates, reviews',
        '#ParisFood #PopUp #FoodieHeaven'),
    Event(
        'Sports celebration in London, UK',
        'SPORTS',
        'City center celebration/viewing party',
        'Streets packed, public transport extended',
        'Celebration videos, fan reactions, chants',
        '#Chelsea #Celebration #Champions'),
    Event(
        'Street art appearance at Melbourne',
        'CULTURE',
        'Massive new Banksy-style mural appears overnight',
        'Crowds gathering, art critics speculating',
        'Art photos, meaning discussions, location sharing',
        '#MelbourneArt #StreetArt #Urban'),
    Event(
        'Public transport delay in Toronto',
        'URBAN',
        'Major subway line technical issues',
        'Morning commute chaos, overflow to buses',
        'Delay updates, alternate route sharing',
        '#TTCAlert #TorontoTransit #CommuterProblems'),
    Event(
        'Unusual animal migration in Perth',
        'NATURE',
        'Wildebeest migration two months earlier than usual',
        'Safari schedule changes, scientific interest',
        'Wildlife photos, expert explanations',
        '#Perth #Wildlife #Migration'),
    Event(
        'Rainbow phenomenon in Dublin',
        'NATURE',
        'Triple rainbow after storm, rare atmospheric event',
        'Traffic slowing for photos, crowds gathering',
        'Rainbow photos, weather explanations',
        '#DublinRainbow #Weather #Ireland'),
    Event(
        'Food market rush in Bangkok',
        'URBAN',
        'Famous street food market reopening after renovation',
        'Massive crowds, traffic diversions',
        'Food reviews, queue updates, recommendations',
        '#BangkokFood #StreetFood #Thailand'),
    Event(
        'Fog covering San Francisco',
        'NATURE',
        'Unusually thick fog creating ethereal city views',
        'Flight delays, dramatic photo opportunities',
        'Fog photos, weather updates, travel advisories',
        '#KarlTheFog #SF #FogCity'),
    Event(
        'Local festival in Rio de Janeiro',
        'CULTURE',
        'Winter carnival pre-event celebration',
        'Beach areas packed, street parades',
        'Dance videos, costume photos, music sharing',
        '#RioCarnival #Brazil #Festival'),
    Event(
        'Public transport strike in Berlin',
        'URBAN',
        'Unexpected transit worker strike',
        'Morning commute disruption, increased bike sharing',
        'Alternative route sharing, strike updates',
        '#BerlinStrike #Transit #BerlinTransport'),
]