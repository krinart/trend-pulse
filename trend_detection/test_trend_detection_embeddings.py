import pytest
import time
from trend_detection_embeddings import TrendDetectorEmbeddings, Message, Trend


class TestTrendDetectorEmbeddings:
   @pytest.fixture
   def detector(self):
       return TrendDetectorEmbeddings(window_minutes=5)

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
           detector.process_message(msg, "LA", current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 1
       
       trend = list(detector.trends.values())[0]
       assert "Lakers" in trend.keywords
       assert "Warriors" in trend.keywords

   def test_multiple_trends(self, detector):
       current_time = time.time()
       
       # Two distinct topics
       sports_msgs = [
           "Lakers beat Warriors in overtime",
           "Amazing Lakers Warriors game",
           "LA vs Golden State thriller"
       ]
       
       traffic_msgs = [
           "Heavy traffic on I-405",
           "Major accident causing delays on 405",
           "Traffic jam in Los Angeles"
       ]
       
       # Interleave messages
       for s, t in zip(sports_msgs, traffic_msgs):
           detector.process_message(s, "LA", current_time)
           detector.process_message(t, "LA", current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 2

   def test_trend_matching(self, detector):
       current_time = time.time()
       
       # Create initial trend
       for msg in ["Lakers beat Warriors", "LA vs Golden State"]:
           detector.process_message(msg, "LA", current_time)
           
       detector.detect_trends(current_time)
       
       # New similar message should match existing trend
       new_msg = "Warriors Lakers game was amazing"
       matched_trend = detector.process_message(new_msg, "LA", current_time + 60)
       
       assert matched_trend is not None
       trend = detector.trends[matched_trend]
       assert len(trend.messages) == 3

   def test_trend_expiration(self, detector):
       current_time = time.time()
       
       # Create trend
       messages = [
           "Lakers beat Warriors",
           "LA vs Golden State",
           "Amazing basketball game"
       ]
       
       for msg in messages:
           detector.process_message(msg, "LA", current_time)
           
       detector.detect_trends(current_time)
       assert len(detector.trends) == 1
       
       # Check after window
       future_time = current_time + (detector.window_minutes * 60) + 1
       detector.cleanup_trends(future_time)
       assert len(detector.trends) == 0

   def test_trend_evolution(self, detector):
       current_time = time.time()
       
       # Initial trend
       initial_msgs = [
           "Lakers start strong against Warriors",
           "LA leads Golden State"
       ]
       
       for msg in initial_msgs:
           detector.process_message(msg, "LA", current_time)
           
       detector.detect_trends(current_time)
       initial_keywords = list(detector.trends.values())[0].keywords
       
       # Evolution
       later_msgs = [
           "Lakers win in overtime",
           "Amazing finish to Lakers Warriors game"
       ]
       
       for msg in later_msgs:
           detector.process_message(msg, "LA", current_time + 300)
           
       # Keywords should update
       final_keywords = list(detector.trends.values())[0].keywords
       assert set(initial_keywords) != set(final_keywords)

   def test_cross_location_trends(self, detector):
       current_time = time.time()
       
       # Same trend, different locations
       detector.process_message("Lakers vs Warriors", "LA", current_time)
       detector.process_message("Warriors playing Lakers", "SF", current_time)
       detector.process_message("LA vs Golden State", "LA", current_time)
       
       detector.detect_trends(current_time)
       
       trend = list(detector.trends.values())[0]
       assert len(trend.locations) == 2
       assert "LA" in trend.locations
       assert "SF" in trend.locations

   def test_message_window_cleanup(self, detector):
       current_time = time.time()
       
       # Add old messages
       old_time = current_time - (detector.window_minutes * 60) - 1
       detector.process_message("Old message", "LA", old_time)
       
       # Add new message
       detector.process_message("New message", "LA", current_time)
       
       detector.clean_window(current_time)
       assert len(detector.messages) == 1