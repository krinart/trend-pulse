export enum TopicEnum {
	SPORT = "SPORT",
	FINANCES = "FINANCES",
	TECH = "TECH",
	ENTERTAINMENT = "ENTERTAINMENT",
	POLITICS = "POLITICS"
}

export interface Location {
	id: number;
	lon: number;
	lat: number;
	name: string;
}

export interface LocationRef {
	locationId: number;
}

export interface Trend {
	id: number;
	name: string;
	is_active: boolean;
	started_at: Date;
	expired_at: Date;
	topic: TopicEnum;
	is_global: boolean;
	locations: LocationRef[];
	heatmap_filename: string;
	timeseries_points?: string[];
	// timeseries_heatmap_filename?: string;
	tile_dir: string,
	min_zoom: number,
	max_zoom: number,
	tileIndex: {[zoom: number]: string[]};
	timeseriesURL: string;
}

export interface LocationTrends {
	id: number;
	name: string;
	lon: number;
	lat: number;
	localTrends: Trend[];
	globalTrends: Trend[];
}

export interface TimePoint {
  timestamp: string;
  value: number;
}