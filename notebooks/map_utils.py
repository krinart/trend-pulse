import folium
import geopandas as gpd
from shapely.geometry import Polygon, MultiPolygon, base
from pyproj import CRS
import json
from datetime import datetime
import matplotlib.pyplot as plt

def display_geometry_on_map(geometry, source_crs='EPSG:3857', center_lat=None, center_lon=None, 
                          zoom_start=10, fill_color='#3388ff', color='#3388ff', 
                          fill_opacity=0.2, weight=2):
    """
    Display any Shapely geometry on an interactive map, handling reprojection
    
    Parameters:
    geometry: Shapely geometry object in Web Mercator (or other projection)
    source_crs: The CRS of the input geometry (default: 'EPSG:3857' for Web Mercator)
    center_lat: Optional center latitude for the map view
    center_lon: Optional center longitude for the map view
    zoom_start: Initial zoom level
    fill_color: Color to fill the geometry
    color: Color of the geometry border
    fill_opacity: Opacity of the fill (0-1)
    weight: Width of the border
    """
    # Verify input is a Shapely geometry
    if not isinstance(geometry, base.BaseGeometry):
        raise ValueError("Input must be a Shapely geometry object")
    
    # Create GeoDataFrame with the source CRS
    gdf = gpd.GeoDataFrame(geometry=[geometry], crs=source_crs)
    
    # Reproject to WGS84 (EPSG:4326)
    gdf = gdf.to_crs('EPSG:4326')
    
    # Get the reprojected geometry
    geometry_4326 = gdf.geometry.iloc[0]
    
    # If center coordinates aren't provided, use geometry centroid
    if center_lat is None or center_lon is None:
        centroid = geometry_4326.centroid
        center_lat, center_lon = centroid.y, centroid.x
    
    # Create a map centered on the geometry
    m = folium.Map(location=[center_lat, center_lon], zoom_start=zoom_start)
    
    # Convert geometry to GeoJSON format
    geo_json = json.loads(gdf.to_json())
    
    # Add geometry to map
    folium.GeoJson(
        geo_json,
        style_function=lambda x: {
            'fillColor': fill_color,
            'color': color,
            'fillOpacity': fill_opacity,
            'weight': weight
        }
    ).add_to(m)
    
    # Add bounds
    bounds = gdf.total_bounds  # [minx, miny, maxx, maxy]
    m.fit_bounds([[bounds[1], bounds[0]], [bounds[3], bounds[2]]])
    
    return m


def plot_polygons_with_labels(gdf, 
                            name_column='name',
                            figsize=(15, 10),
                            title='Polygons with Labels',
                            cmap='Set3',
                            alpha=0.7,
                            fontsize=10):
    """
    Plot polygons from a GeoDataFrame with labels at their centroids.
    
    Parameters:
    -----------
    gdf : GeoDataFrame
        Must contain a geometry column with polygons and a name column
    name_column : str
        Name of the column containing the labels (default: 'name')
    figsize : tuple
        Figure size in inches (default: (15, 10))
    title : str
        Title for the plot (default: 'Polygons with Labels')
    cmap : str
        Matplotlib colormap name (default: 'Set3')
    alpha : float
        Transparency for the polygons (default: 0.7)
    fontsize : int
        Font size for the labels (default: 10)
    
    Returns:
    --------
    fig, ax : matplotlib figure and axis objects
    """
    # Create the plot
    fig, ax = plt.subplots(figsize=figsize)
    
    # Plot polygons
    gdf.plot(ax=ax, 
            cmap=cmap,
            alpha=alpha,
            edgecolor='black',
            linewidth=0.5)
    
    # Add labels at centroids
    for idx, row in gdf.iterrows():
        # Get centroid
        centroid = row.geometry.centroid
        
        # Create label with white background
        ax.annotate(
            text=row[name_column],
            xy=(centroid.x, centroid.y),
            xytext=(0, 0),
            textcoords="offset points",
            fontsize=fontsize,
            ha='center',
            va='center',
            bbox=dict(
                boxstyle="round,pad=0.3",
                fc="white",
                ec="gray",
                alpha=0.8
            )
        )
    
    # Customize the plot
    ax.set_title(title, fontsize=fontsize + 2, pad=20)
    ax.axis('off')  # Remove axes
    
    # Adjust plot limits to show all geometries with some padding
    ax.margins(0.1)
    
    return fig, ax


def plot_trend_timeseries_matplotlib(trend_data, title="Trend Volume Over Time"):
    """
    Plot trend time-series data using matplotlib.
    
    Args:
        trend_data (list): List of dictionaries with 'timestamp' and 'value'
        title (str): Chart title
    """
    # Extract data
    timestamps = [datetime.fromisoformat(point['timestamp']) 
                 if isinstance(point['timestamp'], str) 
                 else point['timestamp'] 
                 for point in trend_data]
    values = [point['value'] for point in trend_data]

    # Create figure and axis
    plt.figure(figsize=(12, 6))
    
    # Plot main line
    plt.plot(timestamps, values, 'b-', linewidth=2, label='Volume')
    
    # Fill area under the line
    plt.fill_between(timestamps, values, alpha=0.2)
    
    # Find peaks
    from scipy.signal import find_peaks
    peaks, _ = find_peaks(values, distance=20, prominence=max(values)*0.1)
    
    # Plot peaks
    if len(peaks) > 0:
        peak_times = [timestamps[idx] for idx in peaks]
        peak_values = [values[idx] for idx in peaks]
        plt.plot(peak_times, peak_values, 'ro', label='Peaks')
    
    # Customize plot
    plt.title(title)
    plt.xlabel('Time')
    plt.ylabel('Volume')
    plt.grid(True, alpha=0.3)
    plt.legend()
    
    # Rotate x-axis labels for better readability
    plt.xticks(rotation=45)
    
    # Adjust layout to prevent label cutoff
    plt.tight_layout()
    
    return plt


# Create and display the map
# m = display_geometry_on_map(CITIES_INFO[('New York', 'NY')]['geometry'])
# m