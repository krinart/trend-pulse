import pytest
import json
from unittest.mock import Mock, patch
from pyflink.common import Types, Row
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.state import ValueState
from datetime import datetime

from processors.trend_detection_processor import TrendDetectionProcessor
from run_flink import PreProcessingMapFunction
from trend_detection_embeddings import TrendDetectorEmbeddings, TREND_CREATED, Trend, TrendEvent

@pytest.fixture
def sample_data():
    return [
        Row(
            text="test message 1",
            timestamp=datetime.now().isoformat(),
            lon=-117.1611,
            lat=32.7157,
            d_trend_id=1,
            d_location_id=1
        ),
        Row(
            text="test message 2",
            timestamp=datetime.now().isoformat(),
            lon=-117.1611,
            lat=32.7157,
            d_trend_id=2,
            d_location_id=2
        )
    ]


@pytest.fixture
def mock_runtime_context():
    context = Mock()
    state = Mock(spec=ValueState)
    state.value.return_value = 0
    context.get_state.return_value = state
    return context


class TestPreProcessingMapFunction:
    def test_map_function(self):
        mapper = PreProcessingMapFunction()
        input_row = Row(
            text="test message",
            timestamp=datetime.now().isoformat(),
            lon=-117.1611,
            lat=32.7157,
            d_trend_id=1,
            d_location_id=1
        )
        
        result = mapper.map(input_row)
        
        assert isinstance(result, Row)
        assert result.text == "test message"
        assert result.topic == "ALL"
        assert result.location_id == 1
        assert result.d_trend_id == 1
        assert result.d_location_id == 1


class TestStatefulProcessor:
    def test_initialization(self):
        processor = TrendDetectionProcessor()
        assert processor.message_count is None
        assert processor.td is None
    
    def test_open(self, mock_runtime_context):
        processor = TrendDetectionProcessor()
        processor.open(mock_runtime_context)
        
        assert processor.message_count is not None
        assert isinstance(processor.td, TrendDetectorEmbeddings)
        
        mock_runtime_context.get_state.assert_called_once()
    
    @patch('time.time', return_value=12345)
    def test_process_element(self, mock_time, mock_runtime_context):
        processor = TrendDetectionProcessor()
        processor.open(mock_runtime_context)
        
        # Create mock context for process_element
        ctx = Mock()
        ctx.get_current_key.return_value = 1
        
        # Create sample input
        input_row = Row(
            text="test message",
            timestamp=datetime.now().isoformat(),
            lat=1.0,
            lon=1.0,
            d_trend_id=1,
            d_location_id=1
        )
        
        # Process element and collect results
        results = list(processor.process_element(input_row, ctx))
        
        # Verify state was updated
        processor.message_count.update.assert_called_once_with(1)
        
        # If no trends were detected, results should be empty
        # If trends were detected, verify the output format
        for result in results:
            assert isinstance(result, Row)
            assert hasattr(result, 'trend_event')
            assert hasattr(result, 'trend_id')
            assert hasattr(result, 'keywords')
            assert hasattr(result, 'location_id')
            assert hasattr(result, 'info')


class _TestIntegration:
    def test_end_to_end_flow(self, sample_data):
        env = StreamExecutionEnvironment.get_execution_environment()
        
        # Create data stream from sample data
        data_stream = env.from_collection(
            collection=sample_data,
            type_info=Types.ROW_NAMED(
                ['text', 'timestamp', 'lon', 'lat', 'd_trend_id', 'd_location_id'],
                [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.INT(), Types.INT()]
            )
        )
        
        # Apply transformations
        result_stream = data_stream.map(
            PreProcessingMapFunction(),
            output_type=Types.ROW_NAMED(
                ['text', 'timestamp', 'lon', 'lat', 'topic', 'location_id', 'd_trend_id', 'd_location_id'],
                [Types.STRING(), Types.STRING(), Types.FLOAT(), Types.FLOAT(), Types.STRING(), Types.INT(), Types.INT(), Types.INT()]
            )
        ).key_by(
            lambda x: x.location_id,
            key_type=Types.INT()
        ).process(
            TrendDetectionProcessor(),
            output_type=Types.ROW_NAMED(
                ['trend_event', 'trend_id', 'keywords', 'location_id', 'info'],
                [Types.STRING(), Types.STRING(), Types.STRING(), Types.INT(), Types.STRING()]
            )
        )
        
        # Execute and verify no exceptions are raised
        try:
            env.execute('Test Flink Job')
        except Exception as e:
            pytest.fail(f"Job execution failed: {str(e)}")
