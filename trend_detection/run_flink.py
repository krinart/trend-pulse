import json
import time
import threading

from pyflink.common import Types, Row
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.functions import KeyedProcessFunction, MapFunction, RuntimeContext
from pyflink.datastream.state import ValueStateDescriptor

import utils
from locations import ALL_LOCATIONS
from preprocessing import preprocess_text
from trend_detection_embeddings import TrendDetectorEmbeddings


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


class StatefulProcessor(KeyedProcessFunction):
    def __init__(self):
        self.message_count = None
        self.td = None

    def open(self, runtime_context: RuntimeContext):
        # Initialize state descriptor
        descriptor = ValueStateDescriptor(
            "message_count",
            Types.INT()
        )
        self.message_count = runtime_context.get_state(descriptor)
        self.td = TrendDetectorEmbeddings()

    def process_element(self, value, ctx: 'KeyedProcessFunction.Context'):
        # Get current count
        current_count = self.message_count.value()
        if current_count is None:
            current_count = 0

        # Update state
        self.message_count.update(current_count + 1)

        events = self.td.process_message(
            value.text, 
            value.timestamp,
            value.lat,
            value.lon,
            time.time(), 
            debug_trend_id=value.d_trend_id, 
            debug_location_id=value.d_location_id)

        for event in events:
            yield Row(
                trend_event=event.trend_event,
                trend_id=event.trend.id,
                keywords=', '.join(event.trend.keywords),
                location_id=ctx.get_current_key(),
                info=f"location:{event.trend.debug_location_ids}; trends:{event.trend.debug_trend_ids}",
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
        StatefulProcessor(),
        output_type=Types.ROW_NAMED(
            ['trend_event', 'trend_id', 'keywords', 'location_id', 'info'],
            [Types.STRING(), Types.STRING(), Types.STRING(), Types.INT(), Types.STRING()]
        )
    ).print()
    
    env.execute('Stateful Flink Job')
    ignore_thread_error()


if __name__ == '__main__':
    main()