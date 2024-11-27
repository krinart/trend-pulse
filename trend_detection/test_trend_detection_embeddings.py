from datetime import datetime
import pytest
import time

from sentence_transformers import SentenceTransformer

from trend_detection_embeddings import TrendDetectorEmbeddings, Message, Trend


model = SentenceTransformer('all-MiniLM-L6-v2')

class TestTrendDetectorEmbeddings:
   @pytest.fixture
   def detector(self):
       return TrendDetectorEmbeddings(model=model, window_minutes=5, cluster_min_samples=2)

   def test_initialize_trend_stats(self, detector):
       # Create test messages
       test_messages = [
           Message(
              id="1",
              text="test message",
              timestamp=datetime(2024, 1, 1, 12, 0),
              lat=47.6062,  # Seattle coordinates
              lon=-122.3321,
              embedding=[],
           ),
           Message(
              id="2", 
              text="another message",
              timestamp=datetime(2024, 1, 1, 12, 2),
              lat=47.6062,  # Same location, different time
              lon=-122.3321,
              embedding=[],
           ),
       ]
        
       # Call the method
       stats = detector.initialize_trend_stats(test_messages)
        
       # Verify the structure and counts
       window_timestamp = "2024-01-01T12:00:00"
       assert window_timestamp in stats
        
       assert set(stats['2024-01-01T12:00:00'].keys()) == {3, 6, 9, 12}

       assert list(stats['2024-01-01T12:00:00'][3].items()) == [((1, 2), 2)]
       assert list(stats['2024-01-01T12:00:00'][6].items()) == [((10, 22), 2)]
       assert list(stats['2024-01-01T12:00:00'][9].items()) == [((82, 178), 2)]
       assert list(stats['2024-01-01T12:00:00'][12].items()) == [((656, 1430), 2)]

   def test_basic_trend_detection(self, detector):
       current_time = time.time()
       
       # Similar messages about Lakers game
       messages = [
           "Lakers beat Warriors in overtime thriller",
           "Amazing Lakers Warriors game going to OT",
           "Lakers vs Warriors in intense overtime battle",
           "LA beats Golden State in close game"
       ]
       
       # Should form one trend
       for msg in messages:
           detector.process_message(msg, datetime.now().isoformat(), 10, 10, current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 1
       
       trend = list(detector.trends.values())[0]
       assert "lakers" in trend.keywords
       assert "warriors" in trend.keywords

   def _test_multiple_trends(self, detector):
       current_time = time.time()
       
       # Two distinct topics
       sports_msgs = [
           "Lakers beat Warriors in overtime thriller",
           "Amazing Lakers Warriors game going to OT",
           "Lakers vs Warriors in intense overtime battle",
           "LA beats Golden State in close game",
       ]
       
       traffic_msgs = [
           "Seattle traffic crawls to standstill on I-5 downtown",
           "Heavy traffic blocks I-5 commuters near Seattle Center",
           "Traffic backup stretches for miles along I-5 through Seattle",
           "Massive traffic plague Seattle drivers on northbound I-5 today",
           "Seattle traffic crawls to standstill on I-5 downtown",
           "Heavy traffic blocks I-5 commuters near Seattle Center",
           "Traffic backup stretches for miles along I-5 through Seattle",
           "Massive traffic plague Seattle drivers on northbound I-5 today",
       ]
       
       # Interleave messages
       for msg in sports_msgs + traffic_msgs:
           detector.process_message(msg, datetime.now().isoformat(), current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 2

   def _test_trend_matching(self, detector):
       current_time = time.time()
       
       # Create initial trend
       for msg in ["Lakers beat Warriors", "LA vs Golden State"]:
           detector.process_message(msg, datetime.now().isoformat(), current_time)
           
       detector.detect_trends(current_time)
       
       # New similar message should match existing trend
       new_msg = "Warriors Lakers game was amazing"
       matched_trend = detector.process_message(msg, datetime.now().isoformat(), current_time + 60)
       
       assert matched_trend is not None
       trend = detector.trends[matched_trend]
       assert len(trend.messages) == 3

   def _test_trend_expiration(self, detector):
       current_time = time.time()
       
       # Create trend
       messages = [
           "Lakers beat Warriors",
           "LA vs Golden State",
           "Amazing basketball game"
       ]
       
       for msg in messages:
           detector.process_message(msg, datetime.now().isoformat(), current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 1
       
       # Check after window
       future_time = current_time + (detector.window_minutes * 60) + 1
       detector.cleanup_trends(future_time)
       assert len(detector.trends) == 0

   def _test_trend_evolution(self, detector):
       current_time = time.time()
       
       # Initial trend
       initial_msgs = [
           "Lakers start strong against Warriors",
           "LA leads Golden State"
       ]
       
       for msg in initial_msgs:
           detector.process_message(msg, datetime.now().isoformat(), current_time)
           
       detector.detect_trends(current_time)
       initial_keywords = list(detector.trends.values())[0].keywords
       
       # Evolution
       later_msgs = [
           "Lakers win in overtime",
           "Amazing finish to Lakers Warriors game"
       ]
       
       for msg in later_msgs:
           detector.process_message(msg, datetime.now().isoformat(), current_time + 300)
           
       # Keywords should update
       final_keywords = list(detector.trends.values())[0].keywords
       assert set(initial_keywords) != set(final_keywords)

   # def test_cross_location_trends(self, detector):
   #     current_time = time.time()
       
   #     # Same trend, different locations
   #     detector.process_message("Lakers vs Warriors", datetime.now().isoformat(), current_time)
   #     detector.process_message("Warriors playing Lakers", datetime.now().isoformat(), current_time)
   #     detector.process_message("LA vs Golden State", datetime.now().isoformat(), current_time)
       
   #     detector.detect_trends(current_time)
       
   #     trend = list(detector.trends.values())[0]
   #     assert len(trend.locations) == 2
   #     assert "LA" in trend.locations
   #     assert "SF" in trend.locations

   def _test_message_window_cleanup(self, detector):
       current_time = time.time()
       
       # Add old messages
       old_time = current_time - (detector.window_minutes * 60) - 1
       detector.process_message("Old message", datetime.now().isoformat(), old_time)
       
       # Add new message
       detector.process_message("New message", datetime.now().isoformat(), current_time)
       
       detector.clean_window(current_time)
       assert len(detector.messages) == 1