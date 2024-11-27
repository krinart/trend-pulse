import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import mapboxgl from 'mapbox-gl';
import type { GeoJSONSource, MapboxGeoJSONFeature } from 'mapbox-gl';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon'
import { MatBadgeModule } from '@angular/material/badge';
import { MatSliderModule } from '@angular/material/slider';
import { firstValueFrom } from 'rxjs';

import { FetchService } from '../services/fetch.service';
import { TopicEnum, Location, Trend, LocationTrends, TimePoint } from '../types';
import { ALL_LOCATIONS_MAP, TOPIC_ICONS, TOPICS, CURRENT_TRENDS } from '../data';


@Component({
  selector: 'app-map',
  standalone: true,
  imports: [
    CommonModule,
    HttpClientModule,
    MatIconModule,
    MatBadgeModule,
    FormsModule,
    MatSliderModule,
  ],
  providers: [FetchService],
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.less']
})
export class MapComponent implements OnInit, OnDestroy {
  private map!: mapboxgl.Map;
  private readonly MAP_CENTER: mapboxgl.LngLatLike = [-107.5795, 37.8283];
  private readonly DEFAULT_ZOOM = 3.5;
  private speed = 1000;
  
  currentTopics = TOPICS;
  
  // Current user selection
  private selectedTopic?: TopicEnum;
  private selectedTrend?: Trend;
  selectedLocationId?: number;

  // All trends
  private currentTrends: Trend[] = []; 

  // Trends filtered based on a user selection
  private trendsTopics: Trend[] = []; // trends used to calculate topic counters
  filteredGlobalTrends: Trend[] = []; // global trends displayed in a list
  filteredLocalTrends: Trend[] = [];  // local trends displayed in a list
  private trendsMap: Trend[] = []; // trends displayed on a map
  private locationTrends: LocationTrends[] = [];  // derived from trendsMap
  
  // MAP
  private renderListener?: (e: any) => void;
  private AllMarkers: { [key: string]: mapboxgl.Marker } = {};

  // Slider
  private canvas!: any;
  sliderMaxValue = 30;     // This will bind to [max]
  sliderCurrentValue = 30;  // This will bind to [value]
  sliderDisabled = true;
  timeseriesPoints: string[] = [];

  private timeseriesData: TimePoint[] = [];
  private maxTimeseriesValue = 0;

  constructor(private fetchService: FetchService,private http: HttpClient) {
  }

  async ngOnInit() {
      this.canvas = document.getElementById('trend-chart')!.querySelector('canvas')!

      const [trends, _] = await Promise.all([
        this.fetchService.getCurrentTrends(),
        this.initializeMap()
      ]);

      console.log(trends);

      // Set trends and run remaining operations after both promises complete
      this.currentTrends = trends;
      this.updateTrends();
      this.setupMapLayers();
      this.updateMapLocations();
  }

  ngOnDestroy() {
    if (this.map) {
      this.map.remove();
    }
  }

  private initializeMap(): Promise<void> {
    mapboxgl.accessToken = 'pk.eyJ1Ijoia3JpbmFydCIsImEiOiJjbTNocGk4bzkwamw4MmtxMmgxbml6M3p3In0.kuos_MirrbMB46W84RJTqQ';

    this.map = new mapboxgl.Map({
      container: 'map',
      style: 'mapbox://styles/mapbox/streets-v11',
      center: this.MAP_CENTER,
      zoom: this.DEFAULT_ZOOM,
      renderWorldCopies: false
    });

    this.map.on('moveend', () => {
      const zoom = Math.floor(this.map.getZoom());
      if (this.selectedLocationId !== undefined && this.selectedTrend == undefined && zoom < 6) {
        this.selectedLocationId = undefined;
        this.showMarkers();
      }
    });

    return new Promise((resolve, error) => {
        this.map.on('load', () => {
            resolve();
        });
    })
  }

  private setupMapLayers() {
    this.map.setLayoutProperty('country-label', 'visibility', 'none');
    this.map.setLayoutProperty('state-label', 'visibility', 'none');
    this.map.setLayoutProperty('settlement-label', 'visibility', 'none');

    this.map.setLayoutProperty('road-number-shield', 'visibility', 'none'); // Highway numbers
    this.map.setLayoutProperty('road-label', 'visibility', 'none');         // Road names

    this.map.addSource('country-boundaries', {
      type: 'vector',
      url: 'mapbox://mapbox.country-boundaries-v1'
    });

    this.map.addLayer({
      id: 'country-boundaries',
      type: 'fill',
      source: 'country-boundaries',
      'source-layer': 'country_boundaries',
      paint: {
        'fill-color': 'gray',
        'fill-opacity': 1
      },
      filter: ['!=', ['get', 'iso_3166_1'], 'US']
    });
  }

  private updateTrends() {
      this.udpateTrendsTopics();
      this.updateTrendsList();
      this.updateTrendsMap();
      this.buildLocationTrends();
  }

  private udpateTrendsTopics() {
    let trends = this.currentTrends;
    if (this.selectedLocationId !== undefined) {
        trends = trends.filter(trend => trend.locations.map(t => t.locationId).includes(this.selectedLocationId!))
    }
    this.trendsTopics = trends;
  }
  private updateTrendsList() {
    let trends = this.currentTrends;
    if (this.selectedTopic !== undefined) {
      trends = trends.filter(trend => trend.topic === this.selectedTopic);
    }
    if (this.selectedLocationId !== undefined) {
        trends = trends.filter(trend => trend.locations.map(t => t.locationId).includes(this.selectedLocationId!))
    }
    
    this.filteredGlobalTrends = trends.filter(trend => trend.is_global);
    this.filteredLocalTrends = trends.filter(trend => !trend.is_global);
  }
  private updateTrendsMap() {
    let trends = this.currentTrends;
    if (this.selectedTopic !== undefined) {
      trends = trends.filter(trend => trend.topic === this.selectedTopic);
    }
    this.trendsMap = trends;
  }

  onTopicClick(topic: TopicEnum) {
    if (this.selectedTopic === topic) {
      this.selectedTopic = undefined;
    } else {
      this.selectedTopic = topic;
    }

    this.selectedTrend = undefined;
    this.updateTrends();
    this.showMarkers();
  }

  checkTopicSelected(topic: TopicEnum): boolean {
    return this.selectedTopic !== undefined && this.selectedTopic === topic;
  }

  getTopicIcon(topic: TopicEnum): string {
    return TOPIC_ICONS[topic];
  }

  getTopicLocalTrendsCount(topic: TopicEnum): number {
    return this.trendsTopics.filter(trend => !trend.is_global && trend.topic == topic).length;
  }

  getTopicGlobalTrendsCount(topic: TopicEnum): number {
    return this.trendsTopics.filter(trend => trend.is_global  && trend.topic == topic).length;
  }

  onTrendClick(trend: Trend) {
    if (this.selectedTrend?.id === trend.id) {
      this.selectedTrend = undefined;

      const zoom = Math.floor(this.map.getZoom());
      if (this.selectedLocationId !== undefined && zoom < 6) {
        this.selectedLocationId = undefined;
      }

    } else {
      this.selectedTrend = trend;
    }

    this.updateTrends();

    if (this.selectedTrend) {
      this.showTrend();
    } else {
      this.showMarkers();
    }
  }

  private async showTrend() {
    this.clearMarkers();
    await this.loadTimeseriesData(this.selectedTrend!);
    this.showTrendHeatmap(this.selectedTrend!);
  }

  private showMarkers() {
    this.clearHeatmap();
    this.updateMapLocations();
    this.sliderDisabled = true;
    this.sliderCurrentValue = this.sliderMaxValue;
    this.resetTimeseriesVisualization();
  }

  checkTrendSelected(trend: Trend): boolean {
    return this.selectedTrend !== undefined && this.selectedTrend.id === trend.id;
  }

  private buildLocationTrends() {
    const locationTrends: { [key: number]: LocationTrends } = {};

    for (const trend of this.trendsMap) {
      for (const locationRef of trend.locations) {
        const location = ALL_LOCATIONS_MAP[locationRef.locationId];

        if (!(location.id in locationTrends)) {
          locationTrends[location.id] = {
            id: location.id,
            name: location.name,
            lon: location.lon,
            lat: location.lat,
            localTrends: [],
            globalTrends: []
          }
        }

        const locationTrend = locationTrends[locationRef.locationId];

        if (trend.is_global) {
          locationTrend.globalTrends.push(trend);
        } else {
          locationTrend.localTrends.push(trend);
        }
      }
    }

    this.locationTrends = Object.values(locationTrends);
  }

  private selectLocation(locationID: number) {
    this.selectedLocationId = locationID;
    this.updateTrends();
    this.clearMarkers();
  }

  resetLocation() {
    this.selectedLocationId = undefined;
    this.selectedTrend = undefined;
    this.updateTrends();
    this.showMarkers();
  }

  getLocationName(locationID: number) {
    return ALL_LOCATIONS_MAP[locationID].name;
  }

  private clearMarkers() {
    if (this.renderListener) {
      this.map.off('render', this.renderListener);
    }

    if (this.map.getLayer('clusters')) {
      this.map.removeLayer('clusters');
    }

    if (this.map.getSource('trends')) {
      this.map.removeSource('trends');
    }

    for (const id in this.AllMarkers) {
        this.AllMarkers[id].remove();
    }
  }

  private updateMapLocations() {
    if (this.selectedLocationId !== undefined) {
      return;
    }

    this.clearMarkers();

    const geojsonData: any = {
        type: 'FeatureCollection',
        features: this.locationTrends.map(location => ({
            type: 'Feature',
            geometry: {
                type: 'Point',
                coordinates: [location.lon, location.lat]
            },
            properties: {
                locationID: location.id,
                name: location.name,
                globalTrends: location.globalTrends,
                localTrends: location.localTrends
            }
        }))
    };

    // Add source with clustering enabled
    this.map.addSource('trends', {
        type: 'geojson',
        data: geojsonData,
        cluster: true,
        clusterMaxZoom: 14,
        clusterRadius: 65
    });

    // DO NOT DELETE. OTHERWISE POINTS/CLUSTERS ARE NOT DISPLAYED
    this.map.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'trends',
        filter: ['has', 'point_count'],
        paint: {
            'circle-color': '#ff0000',
            'circle-radius': 10,
            'circle-opacity': 0
        }
    });

    // Objects for caching markers
    this.AllMarkers = {};
    let markersOnScreen: { [key: string]: mapboxgl.Marker } = {};

    const updateMarkers = async () => {
        const newMarkers: { [key: string]: mapboxgl.Marker } = {};
        const features = this.map.querySourceFeatures('trends');

        for (const feature of features) {
            const props = feature.properties!;
            
            if (!props['cluster']) {
                const point_marker_id = `point_${props['name']}`;

                let marker = this.AllMarkers[point_marker_id];
                if (!marker) {
                  // Handle single points
                  const coords = (feature.geometry as any).coordinates;

                  const marker = this.createMarker(
                      props['name'],
                      JSON.parse(props['globalTrends']).length,
                      JSON.parse(props['localTrends']).length,
                      coords[0],
                      coords[1],
                      false,
                      Number(props['locationID']),
                  );

                  if(marker) {
                    this.AllMarkers[point_marker_id] = marker;
                  }
                }

                if (marker) {
                    newMarkers[point_marker_id] = marker;

                    if (!markersOnScreen[point_marker_id]) {
                        marker.addTo(this.map);
                    }
                }
                continue;
            }

            const cluster_id = `${props['cluster_id']}`;

            let marker = this.AllMarkers[cluster_id];
            if (!marker) {
                const coords = (feature.geometry as any).coordinates;

                const leaves = await this.getClusterLeaves(props['cluster_id']);
                const clusterName = `${leaves[0].properties['name']} + ${leaves.length - 1}`;

                const globalTrends = []
                const localTrends = [];

                for (const leaf of leaves) {
                  globalTrends.push(...leaf.properties['globalTrends'].map((t: Trend) => t.id));
                  localTrends.push(...leaf.properties['localTrends'].map((t: Trend) => t.id));
                }

                const globalCount = [...new Set(globalTrends)].length;
                const localCount = [...new Set(localTrends)].length;

                const marker = this.createMarker(
                      clusterName,
                      globalCount,
                      localCount,
                      coords[0],
                      coords[1],
                      true,
                      Number(cluster_id)
                );  

                if (marker) {
                  this.AllMarkers[cluster_id] = marker;
                }
            }

            newMarkers[cluster_id] = marker;

            if (!markersOnScreen[cluster_id] && marker) {
                marker.addTo(this.map);
            }
        }

        // Remove markers no longer visible
        for (const id in markersOnScreen) {
            if (!newMarkers[id] && markersOnScreen[id]) {
                markersOnScreen[id].remove();
            }
        }
        markersOnScreen = newMarkers;
    };

    this.renderListener = () => {
        if (!this.map.isSourceLoaded('trends')) return;
        updateMarkers();
    };

    // Add the new render listener
    this.map.on('render', this.renderListener);
}

private getClusterLeaves(clusterId: number): Promise<any[]> {
    return new Promise((resolve, reject) => {
        (this.map.getSource('trends') as mapboxgl.GeoJSONSource)
            .getClusterLeaves(clusterId, 100, 0, (err, leaves) => {
                if (err) reject(err);
                else resolve(leaves as any[]);
            });
    });
}

createMarker(title: string, globalCount: number, localCount: number, lon: number, lat: number, isCluster: boolean, locationID: number) {
    if (globalCount == 0 && localCount == 0) {
      return;
     }

     const el = document.createElement('div');
     el.className = 'marker';

     if (!isCluster) {

         el.addEventListener('click', () => {
            this.map.flyTo({
                center: [lon, lat],
                zoom: 9.5
            });
            this.selectLocation(locationID);
        });
      } else {
          el.addEventListener('click', () => {

            (this.map.getSource('trends') as mapboxgl.GeoJSONSource)
              .getClusterExpansionZoom(
                  locationID!,
                  (err, zoom) => {
                      if (err) return;
                      this.map.flyTo({
                          center: [lon, lat],
                          zoom: zoom!+2
                      });
                  }
              );
            
        });
      }

    const self = this;

    if (globalCount > 0) {
         const iconContainer1 = document.createElement('div');
         iconContainer1.className = 'icon-with-badge';
         
         const iconSpan1 = document.createElement('span');
         iconSpan1.className = 'material-icons';
         iconSpan1.textContent = 'public';
         
         const badge1 = document.createElement('span');
         badge1.className = 'badge';
         badge1.textContent = globalCount.toString();
         
         iconContainer1.appendChild(iconSpan1);
         iconContainer1.appendChild(badge1);
         el.appendChild(iconContainer1);
     }

    if (localCount > 0) {
         const iconContainer2 = document.createElement('div');
         iconContainer2.className = 'icon-with-badge';
         
         const iconSpan2 = document.createElement('span');
         iconSpan2.className = 'material-icons';
         iconSpan2.textContent = 'home';
         
         const badge2 = document.createElement('span');
         badge2.className = 'badge';
         badge2.textContent = localCount.toString();
         
         iconContainer2.appendChild(iconSpan2);
         iconContainer2.appendChild(badge2);
         el.appendChild(iconContainer2);
     }
     
     const marker = new mapboxgl.Marker({ element: el })
         .setLngLat([lon, lat]);
         
     const titleDiv = document.createElement('div');
     titleDiv.className = 'title'
     titleDiv.textContent = `${title}`;
     el.appendChild(titleDiv);

     return marker;   
  } 

  private clearHeatmap() {
    if (this.map.getLayer('heatmap')) {
        this.map.removeLayer('heatmap');
    }

    if (this.map.getSource('heatmap')) {
        this.map.removeSource('heatmap');
    }
  }

  private async showTrendHeatmap(trend: Trend) {
    this.clearHeatmap();
    
    // Add empty source
    this.map.addSource('heatmap', {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: []
      }
    });

    // Add heatmap layer with same style as before
    this.addHeatmapLayer();

    if (trend.timeseries_points) {
      this.timeseriesPoints = trend.timeseries_points;
      this.sliderMaxValue = this.timeseriesPoints.length - 1;
      this.sliderCurrentValue = this.sliderMaxValue;
      this.sliderDisabled = false;
    }

    // Initial load of visible tiles
    await this.updateVisibleTiles();

    // Add viewport change listener
    this.map.on('zoom', this.updateVisibleTiles.bind(this));
  }

  private async updateVisibleTiles() {
    if (!this.selectedTrend) return;

    const zoom = Math.floor(this.map.getZoom());
    const bounds = this.map.getBounds();
    
    // Get timestamp if we're showing timeseries data
    const timestamp = this.sliderDisabled ? undefined : 
      this.timeseriesPoints[this.sliderCurrentValue];

    // Load visible tiles
    const tiles = await this.fetchService.getTrendVisibleTiles(
      this.selectedTrend,
      bounds!,
      zoom,
      timestamp
    );

    if (tiles.length === 0) {
      return;
    }

    // Merge tile data
    const mergedData = {
      type: 'FeatureCollection',
      features: tiles.flatMap(tile => tile.features)
    };

    // Update source
    (this.map.getSource('heatmap') as mapboxgl.GeoJSONSource).setData(mergedData as any);
  }

  private addHeatmapLayer() {
    const currentValue = this.timeseriesData[this.sliderCurrentValue]?.value || 0;
    const intensityScale = currentValue / this.maxTimeseriesValue;

    this.map.addLayer({
        id: 'heatmap',
        type: 'heatmap',
        source: 'heatmap',
        paint: {
            'heatmap-weight': [
                'interpolate',
                ['linear'],
                ['get', 'intensity'],
                0, 0,
                1, intensityScale  // Scale max intensity by timeseries value
            ],
            'heatmap-intensity': [
                'interpolate',
                ['linear'],
                ['zoom'],
                0, intensityScale,  // Base intensity scaled by timeseries
                5, intensityScale * 1.5,
                10, intensityScale * 2,
                15, intensityScale * 3
            ],
            // Rest of the paint properties remain the same...
            'heatmap-color': [
                'interpolate',
                ['linear'],
                ['heatmap-density'],
                0, 'rgba(255,0,0,0)',
                0.2, 'rgba(255,195,155,0.8)',
                0.4, 'rgba(255,165,120,0.8)',
                0.6, 'rgba(255,95,85,0.8)',
                0.8, 'rgba(255,45,45,0.8)',
                1, 'rgba(178,0,0,0.8)'
            ],
            'heatmap-radius': [
                'interpolate',
                ['linear'],
                ['zoom'],
                0, 1,
                5.9, 70,
                6.1, 20,
                8.9, 120,
                9.1, 90,
                11.9, 200,
                12.1, 110,
                20, 200
            ],
            'heatmap-opacity': 0.9
        }
    });
  }

  onSliderInput(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.sliderCurrentValue = Number(value);
    
    // Remove and re-add heatmap layer with new scaling
    if (this.map.getLayer('heatmap')) {
        this.map.removeLayer('heatmap');
    }
    this.addHeatmapLayer();
    
    this.updateVisibleTiles();
  }

  private resetTimeseriesVisualization() {
    const ctx = this.canvas.getContext('2d')!;
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
  }

  async loadTimeseriesData(trend: Trend) {
    this.resetTimeseriesVisualization();

    this.timeseriesData = await this.fetchService.getTrendTimeseries(trend);
    this.maxTimeseriesValue = Math.max(...this.timeseriesData.map(point => point.value));

    const canvas = this.canvas;
    const ctx = canvas.getContext('2d')!;
    const width = this.canvas.parentElement?.clientWidth;
    const height = 50;  // adjust as needed
    
    const padding = 10;

    this.canvas.width = width;
    this.canvas.height = height + padding;
    // canvas.style.backgroundColor = 'transparent'
    
    // Draw background
    ctx.fillStyle = 'rgba(255, 255, 255, 0.0)';
    ctx.fillRect(0, 0, width, height);

    const maxValue = Math.max(...this.timeseriesData.map(point => point.value));
    
    // Draw timeseries
    if (this.timeseriesData.length > 1) {
      ctx.beginPath();
      ctx.strokeStyle = '#2196F3';
      ctx.lineWidth = 2;
      
      const xStep = width / (this.timeseriesData.length - 1);
      
      this.timeseriesData.forEach((point, index) => {
        const x = index * xStep;
        const y = height - (point.value / maxValue * height) + padding;
        
        if (index === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      });
      
      ctx.stroke();
    }
  }

  
}