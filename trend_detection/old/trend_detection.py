import spacy

from collections import defaultdict
import itertools
from dataclasses import dataclass
from typing import Set, Dict, List


# import faiss
# import numpy as np
# from sentence_transformers import SentenceTransformer


@dataclass
class Trend:
    entities: Set[str]
    start_time: float
    last_update: float
    mention_count: int
    locations: Set[str]



class TrendDetector:
    MATCH_THRESHOLD = 0.8

    def __init__(self, trend_threshold=10):
        # Counters for unclaimed keywords
        self.free_entity_occurrences = defaultdict(list)  # entity -> [timestamps]
        self.free_co_occurrences = defaultdict(list)  # (e1,e2) -> [timestamps]

        # Active trends
        self.active_trends: Dict[str, Trend] = {}  # trend_id -> Trend

        # Parameters
        self.window_minutes = 5
        self.trend_timeout_minutes = 15

        self.min_trend_threshold = 10
        self.last_trend_check = 0
        self.unprocessed_messages_count = 0
        self.unprocessed_messages_threshold = 10

        self.TREND_THRESHOLD = trend_threshold

        self.nlp = spacy.load("en_core_web_sm")

        # self.model = SentenceTransformer('all-MiniLM-L6-v2')
        #
        # self.topics = ["SPORTS", "TRAFFIC", "WEATHER", "POLITICS"]
        # self.topic_texts = [
        #     "game basketball football soccer score player team sport match",
        #     "traffic road car accident highway jam crash delay transportation",
        #     "rain storm snow temperature wind sunny cloudy forecast weather",
        #     "election government policy debate president vote campaign political"
        # ]
        #
        # topic_vectors = self.model.encode(self.topic_texts)
        # dimension = topic_vectors.shape[1]
        # self.index = faiss.IndexFlatIP(dimension)
        # self.index.add(np.float32(topic_vectors))

    def extract_entities(self, message: str) -> Set[str]:
        doc = self.nlp(message)
        # entities = [(ent.text, ent.label_) for ent in doc.ents],
        entities = [ent.text for ent in doc.ents]

        noun_phrases = [chunk.text for chunk in doc.noun_chunks
                   if not chunk.root.pos_ == 'PRON']

        all_entities = set(entities + noun_phrases)
        all_entities = {e for e in all_entities if not e.startswith('#')}
        return {e.lower() for e in all_entities}  # normalize case

    def process_message(self, message: str, location: str, current_time: float):
        entities = self.extract_entities(message)

        # 1. Try to match to existing trends first
        matched_trend_id = self.match_to_existing_trend(entities, location, current_time)
        if matched_trend_id:
            trend = self.active_trends[matched_trend_id]
            # print(f"Message matching existing trend: {trend.entities}")
            self.update_trend(matched_trend_id, entities, location, current_time)
            return

        # 2. If no match, update free counters
        for entity in entities:
            self.free_entity_occurrences[entity].append(current_time)

        for e1, e2 in itertools.combinations(entities, 2):
            self.free_co_occurrences[(e1, e2)].append(current_time)

        # 3. Periodic trend management
        if current_time - self.last_trend_check > 60 or self.unprocessed_messages_count > self.unprocessed_messages_threshold:
            self.manage_trends(current_time)
            self.last_trend_check = current_time
            self.unprocessed_messages_count = 0
        else:
            self.unprocessed_messages_count += 1

    def manage_trends(self, current_time: float):
        # Clean old data from free counters
        self.evict_old_data(current_time)

        # Check for new trends in free counters
        new_trends = self.find_trending_clusters()
        for trend in new_trends:
            self.create_new_trend(trend, current_time)

        # Update/expire existing trends
        self.manage_existing_trends(current_time)

    def evict_old_data(self, current_time: float):
        cutoff = current_time - (self.window_minutes * 60)

        # Evict old entity occurrences
        for entity in list(self.free_entity_occurrences.keys()):
            self.free_entity_occurrences[entity] = [
                ts for ts in self.free_entity_occurrences[entity]
                if ts > cutoff
            ]
            if not self.free_entity_occurrences[entity]:
                del self.free_entity_occurrences[entity]

        # Evict old co-occurrences
        for pair in list(self.free_co_occurrences.keys()):
            self.free_co_occurrences[pair] = [
                ts for ts in self.free_co_occurrences[pair]
                if ts > cutoff
            ]
            if not self.free_co_occurrences[pair]:
                del self.free_co_occurrences[pair]

    def find_trending_clusters(self) -> List[Dict]:
        trends = []

        # Get counts in current window
        entity_counts = {
            entity: len(timestamps)
            for entity, timestamps in self.free_entity_occurrences.items()
        }

        cooc_counts = {
            pair: len(timestamps)
            for pair, timestamps in self.free_co_occurrences.items()
        }

        # Find clusters of related entities
        used_entities = set()
        clusters = []

        # Sort by co-occurrence count to process strongest relationships first
        sorted_pairs = sorted(cooc_counts.items(), key=lambda x: x[1], reverse=True)

        for (e1, e2), count in sorted_pairs:
            if (count > self.TREND_THRESHOLD and
                    e1 not in used_entities and
                    e2 not in used_entities):

                # Start new cluster
                cluster = {e1, e2}

                # Find other related entities
                for e3 in entity_counts:
                    if e3 not in cluster and e3 not in used_entities:
                        # Check if e3 co-occurs frequently with all entities in cluster
                        if all(cooc_counts.get(tuple(sorted([e, e3])), 0) > self.TREND_THRESHOLD
                               for e in cluster):
                            cluster.add(e3)

                clusters.append(cluster)
                used_entities.update(cluster)

        # Convert clusters to trends
        for cluster in clusters:
            trend = {
                "entities": list(cluster),
                "total_mentions": sum(entity_counts[e] for e in cluster),
                "peak_time": self.find_peak_time(cluster)
            }
            trends.append(trend)

        return trends

    def find_peak_time(self, entities: Set[str]) -> float:
        # Combine all timestamps for the cluster
        all_times = []
        for entity in entities:
            all_times.extend(self.free_entity_occurrences[entity])

        if not all_times:
            return 0

        # Group by minute
        times_by_minute = defaultdict(int)
        for ts in all_times:
            minute = int(ts / 60)
            times_by_minute[minute] += 1

        # Find peak
        peak_minute = max(times_by_minute.items(), key=lambda x: x[1])[0]
        return peak_minute * 60

    def match_to_existing_trend(self, entities: Set[str], location: str, current_time: float) -> str:
        if not entities:
            return

        best_score = 0
        best_trend_id = None

        for trend_id, trend in self.active_trends.items():
            score = self.calculate_match_score(entities, trend, location, current_time)
            if score > best_score:
                best_score = score
                best_trend_id = trend_id

        return best_trend_id if best_score > self.MATCH_THRESHOLD else None

    def calculate_match_score(self, entities, trend, location, current_time):
        shared = entities.intersection(trend.entities)
        time_factor = 1.0 / (1.0 + (current_time - trend.last_update) / 3600)  # decay over hours

        # Calculate ratios both ways
        trend_coverage = len(shared) / len(trend.entities)
        entity_coverage = len(shared) / len(entities)

        location_factor = 1.2 if location in trend.locations else 1.0

        return (trend_coverage + entity_coverage) * time_factor * location_factor

    def update_trend(self, trend_id: str, entities: Set[str], location: str, current_time: float):
        trend = self.active_trends[trend_id]
        trend.last_update = current_time
        trend.mention_count += 1
        trend.locations.add(location)
        # Possibly add new entities if they become relevant

    def manage_existing_trends(self, current_time: float):
        expired_trends = []
        for trend_id, trend in self.active_trends.items():
            # Check if trend is still active

            if current_time - trend.last_update > self.trend_timeout_minutes * 60:
                expired_trends.append(trend_id)

        # Remove expired trends
        for trend_id in expired_trends:
            trend = self.active_trends[trend_id]
            # Release entities back to free pool
            self.release_trend_entities(trend)
            del self.active_trends[trend_id]

    def release_trend_entities(self, trend: Trend):
        # When trend expires, its recent mentions become available for new trends
        # Implementation depends on how you want to handle historical data
        pass

    def create_new_trend(self, trend, current_time: float):
        trend_id = f"trend_{''.join(trend['entities'])}"
        self.active_trends[trend_id] = Trend(
            entities=trend['entities'],
            start_time=current_time,
            last_update=current_time,
            mention_count=1,
            locations=set()
        )

        print(f"New Trend Created: {trend['entities']}")

        # Remove these entities from free counters
        self.claim_entities_for_trend(trend['entities'])

    def claim_entities_for_trend(self, entities: Set[str]):
        # import ipdb; ipdb.set_trace()
        # Remove these entities from free counters
        for entity in entities:
            del self.free_entity_occurrences[entity]

        # Clean up co-occurrences
        for pair in list(self.free_co_occurrences.keys()):
            if pair[0] in entities or pair[1] in entities:
                del self.free_co_occurrences[pair]