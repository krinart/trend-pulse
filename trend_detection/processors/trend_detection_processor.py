from datetime import datetime, timedelta
import time

from pyflink.common import Types, Row
from pyflink.datastream.functions import KeyedProcessFunction, RuntimeContext
from pyflink.datastream.state import ValueStateDescriptor

from trend_detection_embeddings import TrendDetectorEmbeddings
import utils


class TrendDetectionProcessor(KeyedProcessFunction):
    def __init__(self):
        self.td = None
        self.scheduled_windows = None
        self.window_minutes = 5

    def open(self, runtime_context: RuntimeContext):
        self.td = TrendDetectorEmbeddings()

        self.scheduled_windows = runtime_context.get_state(
            ValueStateDescriptor("scheduled_windows", Types.LIST(Types.LONG()))
        )

    def _schedule_window_end_callback(self, ctx, timestamp):
        msg_timestamp = datetime.fromisoformat(timestamp)
        window_start = utils.timestamp_to_window_start(msg_timestamp, self.window_minutes)
        window_end = int((window_start + timedelta(minutes=self.window_minutes)).timestamp())

        scheduled = self.scheduled_windows.value() or []
            
        if window_end not in scheduled:
            ctx.timer_service().register_event_time_timer(window_end)
            scheduled.append(window_end)
            self.scheduled_windows.update(scheduled)

    def process_element(self, value, ctx: 'KeyedProcessFunction.Context'):
        self._schedule_window_end_callback(ctx, value.timestamp)

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

    def on_timer(self, timestamp: int, ctx: 'KeyedProcessFunction.OnTimerContext'):
        pass
        # scheduled = self.scheduled_windows.value()
        # if scheduled is not None:
        #     scheduled.remove(timestamp)
        #     self.scheduled_windows.update(scheduled)
            
        # # Emit stats for all active trends
        # location_id = ctx.get_current_key()
        # window_end = datetime.fromtimestamp(timestamp / 1000)
        # window_start = window_end - timedelta(minutes=5)

        # for trend in self.td.trends.values():
        #     yield Row(
        #         event_type="STATS",
        #         location_id=location_id,
        #         trend_id=trend.id,
        #         window_start=window_start.isoformat(),
        #         window_end=window_end.isoformat(),
        #         stats=json.dumps(trend.stats.stats)
        #     )