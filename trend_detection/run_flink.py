import json
import time
import threading

from pyflink.common import Types, Row
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.functions import MapFunction, RuntimeContext
from pyflink.datastream.state import ValueStateDescriptor

import utils
from locations import ALL_LOCATIONS
from processors.trend_detection_processor import TrendDetectionProcessor

def ignore_thread_error():
    """Ignore the specific GRPC error in the read_grpc_client_inputs thread"""
    for thread in threading.enumerate():
        if thread.name == 'read_grpc_client_inputs':
            thread._stop()


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
    data = json.load(open('data/trend_messages_v23.json'))[:100]

    env = StreamExecutionEnvironment.get_execution_environment()
    
    data_stream = env.from_collection(
        collection=data,
        type_info=Types.ROW_NAMED(
            ['text',         'timestamp',    'lon',         'lat',         'd_trend_id', 'd_location_id'],
            [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.INT(),  Types.INT()]
        )
    )

    # Add transformations
    data_stream.map(
        PreProcessingMapFunction(),
        output_type=Types.ROW_NAMED(
            ['text',         'timestamp',    'lon',         'lat',         'topic',        'location_id', 'd_trend_id', 'd_location_id'],
            [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.STRING(), Types.INT(),   Types.INT(),  Types.INT()]
        )
    ).key_by(
        lambda x: x.location_id,  # key by location,
        key_type=Types.INT(),
    ).process(
        TrendDetectionProcessor(),
        output_type=Types.ROW_NAMED(
            ['trend_event', 'trend_id', 'keywords', 'location_id', 'info'],
            [Types.STRING(), Types.STRING(), Types.STRING(), Types.INT(), Types.STRING()]
        )
    ).print()
    
    env.execute('Stateful Flink Job')
    ignore_thread_error()


if __name__ == '__main__':
    main()