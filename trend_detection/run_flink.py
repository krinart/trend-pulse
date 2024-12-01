import json
import time
import threading
from datetime import datetime

from pyflink.common import Types, Row
from pyflink.common.time import Duration
from pyflink.common.watermark_strategy import WatermarkStrategy, TimestampAssigner
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.functions import MapFunction, RuntimeContext
from pyflink.datastream.state import ValueStateDescriptor

import utils
from locations import ALL_LOCATIONS
from processors.trend_detection_processor import TrendDetectionProcessor
from processors.output import TrendStatsRouter, TimeseriesWriter, TileWriter


def ignore_thread_error():
    """Ignore the specific GRPC error in the read_grpc_client_inputs thread"""
    for thread in threading.enumerate():
        if thread.name == 'read_grpc_client_inputs':
            thread._stop()


class MessageTimestampAssigner(TimestampAssigner):
   
    def extract_timestamp(self, message, record_timestamp):
        # print(message.timestamp, record_timestamp, int(datetime.fromisoformat(message.timestamp).timestamp() * 1000))
        return int(datetime.fromisoformat(message.timestamp).timestamp())


class PreProcessingMapFunction(MapFunction):

    def map(self, value):   
        location_id = utils.find_nearest_location(value.lat, value.lon, ALL_LOCATIONS)

        return Row(
            text=value.text,
            timestamp=value.timestamp,
            lat=value.lat,
            lon=value.lon,
            topic="ALL",
            location_id=location_id,
            d_trend_id=value.d_trend_id,
            d_location_id=value.d_location_id,
        )


def main():
    window_minutes = 60
    data = json.load(open('data/trend_messages_v23.json'))

    env = StreamExecutionEnvironment.get_execution_environment()

    output_path = "./output4"
    
    data_stream = env.from_collection(
        collection=data,
        type_info=Types.ROW_NAMED(
            ['text',         'timestamp',    'lon',         'lat',         'd_trend_id', 'd_location_id'],
            [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.INT(),  Types.INT()]
        )
    ).assign_timestamps_and_watermarks(
        WatermarkStrategy
            .for_bounded_out_of_orderness(Duration.of_seconds(5))
            .with_timestamp_assigner(MessageTimestampAssigner())
    )

    # Add transformations
    processed_stream = data_stream.map(
        PreProcessingMapFunction(),
        output_type=Types.ROW_NAMED(
            ['text',         'timestamp',    'lon',         'lat',         'topic',        'location_id', 'd_trend_id', 'd_location_id'],
            [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.STRING(), Types.INT(),   Types.INT(),  Types.INT()]
        )
    ).key_by(
        lambda x: x.location_id,  # key by location,
        key_type=Types.INT(),
    ).process(
        TrendDetectionProcessor(window_minutes=window_minutes),
        output_type=Types.ROW_NAMED(
            ['event_type',   'trend_id',     'location_id', 'event_info'],
            [Types.STRING(), Types.STRING(), Types.INT(),   Types.STRING()]
        )
    )

    # Create and apply router
    stats_router = TrendStatsRouter(output_path)
    routed_stream = processed_stream.process(stats_router)
    
    timeseries_stream = routed_stream.get_side_output(stats_router.timeseries_output)
    timeseries_stream.process(TimeseriesWriter(output_path))
    
    tile_stream = routed_stream.get_side_output(stats_router.tile_output)
    tile_stream.process(TileWriter(output_path))
    
    env.execute('Stateful Flink Job')
    ignore_thread_error()


if __name__ == '__main__':
    main()