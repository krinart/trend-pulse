from pyflink.common import Types, Row
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.functions import MapFunction, RuntimeContext
from preprocessing import preprocess_text
# from pyflink.datastream.functions import MapFunction

import json
data = json.load(open('data/local_events_messages_3.json'))

class PreProcessingMapFunction(MapFunction):

    def map(self, value):   
        return Row(
            text=value.text,
            pre_processed_text=preprocess_text(value.text),
            topic="ALL",
            location="LA",
        )


def main():
    # Create execution environment
    env = StreamExecutionEnvironment.get_execution_environment()
    
    # Create a simple source
    data_stream = env.from_collection(
        collection=data[:2],
        type_info=Types.ROW_NAMED(
            ['text'],
            [Types.STRING()],
        ),
    )
    
    # Add transformation
    data_stream.map(
        PreProcessingMapFunction(),
        output_type=Types.ROW_NAMED(
            ['text', 'pre_processed_text', 'topic', 'location'],
            [Types.STRING(), Types.STRING(), Types.STRING(), Types.STRING()],
        ),
    ).print()
    
    # Execute
    env.execute('Simple Flink Job')

if __name__ == '__main__':
    main()