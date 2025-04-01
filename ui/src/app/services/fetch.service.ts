// fetch.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { map } from 'rxjs/operators';
import { firstValueFrom } from 'rxjs';

import { TopicEnum, LocationRef, Trend, TimePoint } from '../types';


@Injectable({
  providedIn: 'root'
})
export class FetchService {
  constructor(private http: HttpClient) { }

  fileCache: {[key: string]: any} = {};
  private FOLDER_NAME = 'v21';

  BASE_URL = "/";
  TRENDS_URL = `${this.BASE_URL}data/${this.FOLDER_NAME}/trends.json`;

  getCurrentTrends(): Promise<Trend[]> {
    return firstValueFrom(
        this.http.get(this.TRENDS_URL).pipe(
          map(response => parseTrends(response as object[]))
        )
      );
  }

  private fetchFile(path: string): Promise<any> {
    return new Promise((resolve, error) => {
      if (path in this.fileCache) {
        resolve(this.fileCache[path]);
        return;
      }

      this.http.get(path)
        .toPromise()
        .then(response => {
          this.fileCache[path] = response;
          resolve(response);
        });
    })
  }

  getTrendTimeseries(trend: Trend): Promise<TimePoint[]> {
      return firstValueFrom(
        this.http.get<TimePoint[]>(`${this.BASE_URL}/${trend.timeseriesURL}`)
        )
          .then(data => data.sort((a, b) => 
            new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
          ));
  }

  private tileCache: {[key: string]: any} = {};

  getTileUrl(trend: Trend, zoom: number, x: number, y: number, timestamp?: string): string {
    const basePath = trend.tile_dir;
    if (timestamp) {
      return `${basePath}/timeseries/${timestamp}/${zoom}/${x}_${y}.geojson`;
    }
    return `${basePath}/${zoom}/${x}_${y}.geojson`;
  }

  async getTrendTile(trend: Trend, zoom: number, x: number, y: number, timestamp?: string): Promise<any> {
    const tilePath = this.getTileUrl(trend, zoom, x, y, timestamp);
    const cacheKey = `${trend.id}_${zoom}_${x}_${y}_${timestamp || ''}`;
    
    if (this.tileCache[cacheKey]) {
      return this.tileCache[cacheKey];
    }

    const tileUrl = `${this.BASE_URL}/${tilePath}`

    try {
      const response = await firstValueFrom(this.http.get(tileUrl));
      this.tileCache[cacheKey] = response;
      return response;
    } catch (e) {
      if ((e as any).status !== 404) {  // 404 is expected for empty tiles
        console.error(`Error loading tile: ${tileUrl}`, e);
      }
      return null;
    }
  }

  async getTrendVisibleTiles(trend: Trend, bounds: mapboxgl.LngLatBounds, zoom: number, timestamp?: string): Promise<any[]> {
    console.log(zoom);

    // Find biggest zoom which is smaller that requested    
    let zooms = Object.keys(trend.tileIndex).map(k => Number(k)).filter(v => v<=zoom);
    zooms.sort((a, b) => a - b);
    zoom = zooms[zooms.length - 1];

    // Get available tiles for this zoom
    const availableTiles: string[] = trend.tileIndex[zoom] || [];

    // console.log(`availableTiles: ${availableTiles}`);

    // Convert bounds to tile coordinates
    const neededTiles = this.getBoundsTiles(bounds, zoom).map(({x, y}) => `${x}_${y}`)

    // console.log(`neededTiles: ${neededTiles}`);
      
    const foundTiles = neededTiles.filter(tileId => availableTiles.includes(tileId));

    // console.log(zoom, `foundTiles: ${foundTiles}`);

    // Load all visible tiles
    const tilePromises = foundTiles.map(tileId => {
      const [x, y] = tileId.split('_').map(Number);
      return this.getTrendTile(trend, zoom, x, y, timestamp);
    });

    // Wait for all tiles and filter out empty ones
    const tileData = await Promise.all(tilePromises);
    return tileData.filter(tile => tile !== null);
  }

  private getBoundsTiles(bounds: mapboxgl.LngLatBounds, zoom: number): {x: number, y: number}[] {
    const tiles = [];
    const nw = this.latLonToTile(bounds.getNorth(), bounds.getWest(), zoom);
    const se = this.latLonToTile(bounds.getSouth(), bounds.getEast(), zoom);

    for (let x = nw.x; x <= se.x; x++) {
      for (let y = nw.y; y <= se.y; y++) {
        tiles.push({x, y});
      }
    }
    return tiles;
  }

  private latLonToTile(lat: number, lon: number, zoom: number): {x: number, y: number} {
      const n = Math.pow(2, zoom);
      // Handle negative longitudes properly by adding 180 before division
      const x = Math.floor(((lon + 180) / 360) * n);
      const latRad = lat * Math.PI / 180;
      const y = Math.floor(((1 - Math.asinh(Math.tan(latRad)) / Math.PI) / 2) * n);
      return { 
          // Ensure coordinates are non-negative and within bounds
          x: Math.max(0, Math.min(n - 1, x)),
          y: Math.max(0, Math.min(n - 1, y))
      };
  }
}

function parseTrends(rawJson: object[]): Trend[] {
  try {
    // Map each object to a Trend with proper types
    return rawJson.map((item: any): Trend => ({
      id: item.id,
      name: item.name,
      is_active: item.is_active,
      started_at: new Date(item.started_at),
      expired_at: new Date(item.expired_at),
      topic: item.topic as TopicEnum,
      is_global: item.is_global,
      heatmap_filename: item.heatmap_filename,
      timeseries_points: item.timeseries_points,
      tile_dir: item.tile_dir,
      min_zoom: Number(item.min_zoom),
      max_zoom: Number(item.min_zoom),
      tileIndex: item.tile_index,
      timeseriesURL: item.timeseries_url,
      locations: item.locations.map((loc: any): LocationRef => ({
        locationId: loc.locationId
      }))
    }));
  } catch (error) {
    console.error('Error parsing trends:', error);
    throw new Error('Failed to parse trends data');
  }
}
