from pyflink.datastream import StreamExecutionEnvironment
from pyflink.common.watermark_strategy import WatermarkStrategy, TimestampAssigner
from pyflink.datastream.functions import ProcessFunction
from pyflink.common import Duration

# Custom TimestampAssigner
class CustomTimestampAssigner(TimestampAssigner):
    def extract_timestamp(self, element, record_timestamp):
        return element[1]

# Custom ProcessFunction
class PrintWatermarkProcessFunction(ProcessFunction):
    def process_element(self, value, ctx: ProcessFunction.Context):
        current_watermark = ctx.timer_service().current_watermark()
        print(f"Event: {value}, Current Watermark: {current_watermark}")
        yield value  # Forward event

# Execution Environment
env = StreamExecutionEnvironment.get_execution_environment()
env.set_parallelism(1)

# Create a data source with more events to better illustrate watermarks
events = [
    (1, 1000),
    (2, 2000),
    (3, 3000),
    (4, 4000),
    (5, 5000)
]
source = env.from_collection(events)

# WatermarkStrategy with 1 second out-of-orderness
watermark_strategy = (
    WatermarkStrategy.for_bounded_out_of_orderness(Duration.of_seconds(1))
    .with_timestamp_assigner(CustomTimestampAssigner())
)

# Assign watermarks
watermarked_stream = source.assign_timestamps_and_watermarks(watermark_strategy)

# Print watermark progression
processed_stream = watermarked_stream.process(PrintWatermarkProcessFunction())

# Print the events
processed_stream.print()

# Execute the job
env.execute("Flink Job with Proper Watermark Emission")

