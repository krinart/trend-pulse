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

        events = self.td.process_message(value.text, "LA", time.time())

        for event in events:
            yield Row(
                trend_id=event.trend_id,
                trend_event=event.trend_event,
                trend_info=event.trend_info,
            )


class PreProcessingMapFunction(MapFunction):


    def map(self, value):   
        lat=34.0522
        lon=-118.2437
        location_id = utils.find_nearest_location(float(lat), float(lon), ALL_LOCATIONS)

        return Row(
            text=value.text,
            topic="ALL",
            location=location_id
        )


def main():
    data = json.load(open('data/local_events_messages_3.json'))[:10]

    env = StreamExecutionEnvironment.get_execution_environment()
    
    data_stream = env.from_collection(
        collection=data,
        type_info=Types.ROW_NAMED(
            ['text'],
            [Types.STRING()]
        )
    )
    
    # Add transformations
    data_stream.map(
        PreProcessingMapFunction(),
        output_type=Types.ROW_NAMED(
            ['text', 'topic', 'location'],
            [Types.STRING(), Types.STRING(), Types.INT()]
        )
    ).key_by(
        lambda x: x[2],  # key by location,
        key_type=Types.INT(),
    ).process(
        StatefulProcessor(),
        output_type=Types.ROW_NAMED(
            ['trend_id', 'trend_event', 'trend_info'],
            [Types.STRING(), Types.STRING(), Types.STRING()]
        )
    ).print()
    
    env.execute('Stateful Flink Job')
    ignore_thread_error()


if __name__ == '__main__':
    main()