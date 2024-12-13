import time
import pytest
from trend_detection import TrendDetector, Trend


class TestTrendDetector:
    @pytest.fixture
    def detector(self):
        return TrendDetector(trend_threshold=1)

    def test_entity_extraction(self, detector):
        # Test basic entity extraction
        text = "LeBron James scored 35 points as Lakers beat Warriors"
        entities = detector.extract_entities(text)
        assert "lebron james" in entities
        assert "lakers" in entities
        assert "warriors" in entities

        # Test pronoun filtering
        text = "He scored a basket"
        entities = detector.extract_entities(text)
        assert "he" not in entities

    def test_trend_matching(self, detector):
        current_time = time.time()

        # Create an active trend
        trend_entities = {"lakers", "warriors", "basketball"}
        detector.active_trends["test_trend"] = Trend(
            entities=trend_entities,
            start_time=current_time,
            last_update=current_time,
            mention_count=1,
            locations={"LA"}
        )

        # Test matching message
        message = "Lakers vs Warriors game was amazing"
        matched = detector.match_to_existing_trend(
            detector.extract_entities(message),
            "LA",
            current_time
        )
        assert matched == "test_trend"

        # Test non-matching message
        message = "Traffic is bad today"
        matched = detector.match_to_existing_trend(
            detector.extract_entities(message),
            "LA",
            current_time
        )
        assert matched is None

    def test_trend_lifecycle(self, detector):
        current_time = time.time()

        # Test trend creation
        messages = [
            "Lakers beat Warriors in overtime",
            "Amazing Lakers Warriors game",
            "Lakers win against Warriors"
        ]

        # Process messages
        for msg in messages:
            detector.process_message(msg, "LA", current_time)

        # Force trend check
        detector.manage_trends(current_time)

        # Should have created a trend
        assert len(detector.active_trends) > 0

        # Test trend expiration
        future_time = current_time + (detector.trend_timeout_minutes * 60) + 1
        detector.manage_trends(future_time)

        # Trend should be expired
        assert len(detector.active_trends) == 0

    def test_counter_management(self, detector):
        current_time = time.time()

        # Add some entities
        detector.free_entity_occurrences["test"] = [
            current_time - 360,  # 6 minutes ago
            current_time - 60,  # 1 minute ago
            current_time  # now
        ]

        # Clean old data
        detector.evict_old_data(current_time)

        # Should only have recent entries
        assert len(detector.free_entity_occurrences["test"]) == 2

    def test_trend_detection_threshold(self, detector):
        current_time = time.time()

        # Generate messages below threshold
        for _ in range(detector.TREND_THRESHOLD):
            detector.process_message(
                "Lakers beat Warriors game",
                "LA",
                current_time
            )

        detector.manage_trends(current_time)
        assert len(detector.active_trends) == 0

        # Add one more to exceed threshold
        detector.process_message(
            "Lakers beat Warriors game",
            "LA",
            current_time
        )

        detector.manage_trends(current_time)
        assert len(detector.active_trends) > 0

    def test_multiple_trends(self, detector):
        current_time = time.time()

        # Create two separate trends
        sports_msgs = ["Lakers beat Warriors game"] * (detector.TREND_THRESHOLD + 1)
        traffic_msgs = ["Traffic jam on I-405"] * (detector.TREND_THRESHOLD + 1)

        for msg in sports_msgs + traffic_msgs:
            detector.process_message(msg, "LA", current_time)

        detector.manage_trends(current_time)

        # Should detect both trends
        assert len(detector.active_trends) == 2

    def test_matching(self, detector):
        current_time = time.time()
        trend = Trend(
            entities=['temples', 'tokyo', 'hanami', 'sakura'],
            start_time = current_time,
            last_update=current_time,
            mention_count=1,
            locations=['LA'],
        )

        entities = detector.extract_entities("Wow, Tokyo\'s Sakura at its peak bloom  in 15 years! Can\'t wait to join the Hanami party 🌸 But major parks will suffer from crowding 😕 #Tokyo #Spring #Sakura")

        result = detector.calculate_match_score(entities, trend, 'LA', current_time)

        assert False, result