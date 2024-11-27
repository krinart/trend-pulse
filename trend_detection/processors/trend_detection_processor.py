import time

from pyflink.common import Types, Row
from pyflink.datastream.functions import KeyedProcessFunction, RuntimeContext
from pyflink.datastream.state import ValueStateDescriptor

from trend_detection_embeddings import TrendDetectorEmbeddings


class TrendDetectionProcessor(KeyedProcessFunction):
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