import os
import json

from pyflink.common.typeinfo import Types
from pyflink.datastream import OutputTag
from pyflink.datastream.functions import ProcessFunction


TREND_STATS = 'TREND_STATS'


class TrendStatsRouter(ProcessFunction):
    def __init__(self, base_path="./output"):
        self.base_path = base_path
        
        self.timeseries_output = OutputTag(
            "timeseries", 
            Types.TUPLE([Types.STRING(), Types.STRING()])  # (trend_id, json_line)
        )
        
        self.tile_output = OutputTag(
            "tiles", 
            Types.TUPLE([Types.STRING(), Types.STRING()])  # (file_path, tile_data)
        )

    def process_element(self, value, ctx: 'ProcessFunction.Context'):
        if value.event_type != TREND_STATS:
            return

        event_info = json.loads(value.event_info)
        trend_id = value.trend_id
        timestamp = event_info['window_start']
        
        timeseries_item = json.dumps({
            "timestamp": timestamp,
            "count": event_info['stats']['count']
        })
        
        yield self.timeseries_output, (trend_id, timeseries_item)

        for zoom_stats in event_info['stats']['stats']:
            zoom = zoom_stats['zoom']
            for tile in zoom_stats['stats']:
                tile_x = tile['tile_x']
                tile_y = tile['tile_y']
                
                tile_path = f"{trend_id}/timeseries/{timestamp}/{zoom}/{tile_x}_{tile_y}.json"
                tile_data = json.dumps(tile['sampled_points'])
                
                yield self.tile_output, (tile_path, tile_data)


class TimeseriesWriter(ProcessFunction):
    def __init__(self, base_path):
        self.base_path = base_path

    def process_element(self, value, ctx):
        trend_id, line = value
        file_path = os.path.join(self.base_path, f"{trend_id}/timeseries.json")
        
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        
        with open(file_path, 'a') as f:
            f.write(line + '\n')


class TileWriter(ProcessFunction):
    def __init__(self, base_path):
        self.base_path = base_path

    def process_element(self, value, ctx):
        file_path, tile_data = value
        full_path = os.path.join(self.base_path, file_path)
        
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        
        with open(full_path, 'w') as f:
            f.write(tile_data)
