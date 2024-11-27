import { TopicEnum, Location, Trend, LocationTrends } from './types';

// California
export const SAN_DIEGO: Location = { id: 1, lat: 32.7157, lon: -117.1611, name: 'San Diego' };
export const LOS_ANGELES: Location = { id: 2, lat: 34.0522, lon: -118.2437, name: 'Los Angeles' };
export const SAN_FRANCISCO: Location = { id: 3, lat: 37.7749, lon: -122.4194, name: 'San Francisco' };

// // Florida
export const ORLANDO: Location = { id: 4, lat: 28.5384, lon: -81.3789, name: 'Orlando' };
export const MIAMI: Location = { id: 5, lat: 25.7617, lon: -80.1918, name: 'Miami' };

// PNW (Pacific Northwest)
export const SEATTLE: Location = { id: 6, lat: 47.6062, lon: -122.3321, name: 'Seattle' };
export const PORTLAND: Location = { id: 7, lat: 45.5155, lon: -122.6789, name: 'Portland' };

// Great Lakes
export const CHICAGO: Location = { id: 8, lat: 41.8781, lon: -87.6298, name: 'Chicago' };
export const PITTSBURGH: Location = { id: 9, lat: 40.4406, lon: -79.9959, name: 'Pittsburgh' };
export const DETROIT: Location = { id: 10, lat: 42.3314, lon: -83.0458, name: 'Detroit' };
export const COLUMBUS: Location = { id: 11, lat: 39.9612, lon: -82.9988, name: 'Columbus' };

// // Northeast Corridor
export const PHILADELPHIA: Location = { id: 12, lat: 39.9526, lon: -75.1652, name: 'Philadelphia' };
export const BALTIMORE: Location = { id: 13, lat: 39.2904, lon: -76.6122, name: 'Baltimore' };
export const BOSTON: Location = { id: 14, lat: 42.3601, lon: -71.0589, name: 'Boston' };
export const WASHINGTON_DC: Location = { id: 15, lat: 38.9072, lon: -77.0369, name: 'Washington DC' };
export const NEW_YORK: Location = { id: 16, lat: 40.7128, lon: -74.0060, name: 'New York' };

// // Texas Triangle
export const AUSTIN: Location = { id: 17, lat: 30.2672, lon: -97.7431, name: 'Austin' };
export const DALLAS: Location = { id: 18, lat: 32.7767, lon: -96.7970, name: 'Dallas' };
export const HOUSTON: Location = { id: 19, lat: 29.7604, lon: -95.3698, name: 'Houston' };
export const SAN_ANTONIO: Location = { id: 20, lat: 29.4241, lon: -98.4936, name: 'San Antonio' };

export const ALL_LOCATIONS: Location[] = [
  SAN_DIEGO, LOS_ANGELES, SAN_FRANCISCO, 
  ORLANDO, MIAMI,
  SEATTLE, PORTLAND,
  CHICAGO, PITTSBURGH, DETROIT, COLUMBUS, 
  PHILADELPHIA, BALTIMORE, BOSTON, WASHINGTON_DC, NEW_YORK, 
  AUSTIN, DALLAS, HOUSTON, SAN_ANTONIO,
];

export const ALL_LOCATIONS_MAP: { [key: number]: Location } = Object.fromEntries(
    ALL_LOCATIONS.map(location => [location.id, location])
);

export const TOPIC_ICONS: { [key in TopicEnum]: string } = {
  [TopicEnum.SPORT]: "sports_football",
  [TopicEnum.FINANCES]: "attach_money",
  [TopicEnum.TECH]: "computer",
  [TopicEnum.ENTERTAINMENT]: "live_tv",
  [TopicEnum.POLITICS]: "groups",
};

export const TOPICS = [
  {"name": "Sport", "topicEnum": TopicEnum.SPORT},
  {"name": "Finances", "topicEnum": TopicEnum.FINANCES},
  {"name": "Technology", "topicEnum": TopicEnum.TECH},
  {"name": "Entertainment", "topicEnum": TopicEnum.ENTERTAINMENT},
  {"name": "Politics", "topicEnum": TopicEnum.POLITICS},
];

export const CURRENT_TRENDS: Trend[] =[
  // {
  //   "id": 1,
  //   "name": "WorldCup",
  //   "is_active": true,
  //   "started_at": new Date(),
  //   "expired_at": new Date(),
  //   "is_global": true,
  //   "topic": TopicEnum.SPORT,
  //   "locations": [
  //     {"locationId": 2},
  //     {"locationId": 3},
  //     {"locationId": 4},
  //     {"locationId": 5},
  //     {"locationId": 6},
  //     {"locationId": 7},
  //     {"locationId": 8},
  //     {"locationId": 9},
  //     {"locationId": 10},
  //     {"locationId": 11},
  //     {"locationId": 12},
  //     {"locationId": 13},
  //     {"locationId": 14},
  //     {"locationId": 15},
  //     {"locationId": 16},
  //     {"locationId": 17},
  //     {"locationId": 18},
  //     {"locationId": 19},
  //     {"locationId": 20},
  //   ],
  // },
  // {
  //   "id": 2,
  //   "name": "Bitcoin",
  //   "is_active": true,
  //   "started_at": new Date(),
  //   "expired_at": new Date(),
  //   "is_global": true,
  //   "topic": TopicEnum.FINANCES,
  //   "locations": [{"locationId": 1}, {"locationId": 2}],
  // },
  // {
  //   "id": 3,
  //   "name": "Eminem",
  //   "is_active": true,
  //   "started_at": new Date(),
  //   "expired_at": new Date(),
  //   "is_global": true,
  //   "topic": TopicEnum.ENTERTAINMENT,
  //   "locations": [{"locationId": 1}, {"locationId": 2}],
  // },
  // {
  //   "id": 4,
  //   "name": "Formula 1",
  //   "is_active": true,
  //   "started_at": new Date(),
  //   "expired_at": new Date(),
  //   "is_global": true,
  //   "topic": TopicEnum.SPORT,
  //   "locations": [{"locationId": 1}],
  // },
  // {
  //   "id": 5,
  //   "name": "NyConference",
  //   "is_active": true,
  //   "started_at": new Date(),
  //   "expired_at": new Date(),
  //   "is_global": false,
  //   "topic": TopicEnum.TECH,
  //   "locations": [{"locationId": 16}], // New York
  // },
];