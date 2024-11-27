from dataclasses import dataclass
from typing import List, Set, Dict
import time
import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.cluster import DBSCAN
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import uuid

import logging
logging.getLogger('sentence_transformers').setLevel(logging.ERROR)


import preprocessing

@dataclass
class Message:
    id: str
    text: str
    timestamp: float
    location: str
    embedding: np.ndarray


@dataclass 
class Trend:
    id: str
    messages: List[Message]
    keywords: List[str]
    centroid: np.ndarray
    created_at: float
    last_update: float
    locations: Set[str]
    original_messages_cnt: int
    matched_messages_cnt: int


TREND_CREATED = 'TREND_CREATED'


@dataclass
class TrendEvent:
    trend_id: int
    trend_event: int
    trend_info: str


    

class TrendDetectorEmbeddings:
    def __init__(self, model=None, window_minutes=5, cluster_min_samples=10, cluster_eps=0.7):
        if model is not None:
            self.model = model
        else:
            self.model = SentenceTransformer('all-MiniLM-L6-v2')

        self.window_minutes = window_minutes
       
        # Store messages in window
        self.messages: Dict[str, Message] = {}
       
        # Active trends
        self.trends: Dict[str, Trend] = {}
       
        # Parameters
        self.similarity_threshold = 0.8
        self.cluster_min_samples = cluster_min_samples
        self.last_clustering = 0
        self.clustering_interval = 60  # seconds
        self.cluster_eps = cluster_eps

        self.unprocessed_messages_count = 0
        self.unprocessed_messages_threshold = 20
       
    def process_message(self, text: str, location: str, current_time: float):
        # Create message object
        text = preprocessing.preprocess_text(text)

        detected_trends = []

        embedding = self.model.encode([text])[0]
        message = Message(str(uuid.uuid4()), text, current_time, location, embedding)
       
        # Clean old messages
        self.clean_window(current_time)
       
        # Try to match to existing trend
        matched_trend = self.match_to_trend(message)
        if matched_trend:
            self.update_trend(matched_trend, message)
        else:
            # Add to window
            self.messages[message.id] = message
           
        # Periodically look for new trends
        if current_time - self.last_clustering > self.clustering_interval or self.unprocessed_messages_count >= self.unprocessed_messages_threshold:
            detected_trends = self.detect_trends(current_time)
            self.last_clustering = current_time
            self.unprocessed_messages_count = 0
        else:
            self.unprocessed_messages_count += 1

        return self.create_events(detected_trends)

    def create_events(self, detected_trends):
        events = []
        for trend in detected_trends:
            events.append(
                TrendEvent(
                    trend_id=trend.id, 
                    trend_event=TREND_CREATED, 
                    trend_info=', '.join(trend.keywords),
            ))
        return events
           
    def clean_window(self, current_time: float):
        return
        cutoff = current_time - (self.window_minutes * 60)
        # self.messages = [m for m in self.messages if m.timestamp > cutoff]
        self.messages = {m.id: m for m in self.messages.values() if m.timestamp > cutoff}
       
    def match_to_trend(self, message: Message) -> str:
        for trend_id, trend in self.trends.items():
            # Get average similarity with trend messages
            # similarities = [np.dot(message.embedding, m.embedding) 
            #                 for m in trend.messages[-10:]]  # use last 10 messages
            # avg_similarity = np.mean(similarities)

            avg_similarity = self.check_matches_cluster_cosine(trend.centroid, message.embedding)
           
            if avg_similarity > self.similarity_threshold:
                # print(f'Matches trend: {trend.keywords}')
                return trend_id
        return None
       
    def detect_trends(self, current_time: float):
        detected_trends = []

        if len(self.messages) < self.cluster_min_samples:
            return []
           
        # Get embeddings matrix
        messages = self.messages.values()
        embeddings = np.array([m.embedding for m in messages])
       
        # Run clustering
        clustering = DBSCAN(eps=self.cluster_eps, min_samples=self.cluster_min_samples)
        labels = clustering.fit_predict(embeddings)
       
        # Process clusters
        unique_labels = set(labels)
        for label in unique_labels:
            if label == -1:  # noise
                continue
               
            # Get messages in cluster
            cluster_messages = [m for i, m in enumerate(messages) if labels[i] == label]
                             
            embeddings = [m.embedding for m in cluster_messages]
            cluster_centroid = np.array(embeddings).mean(axis=0)

            # Check if matches existing trend
            matched = False
            for trend in self.trends.values():
                # similarities = [np.dot(cluster_messages[0].embedding, m.embedding)  
                #                 for m in trend.messages[-5:]]
                # avg_similarity = np.mean(similarities)

                avg_similarity = self.check_matches_cluster_cosine(cluster_centroid, trend.centroid)

                if avg_similarity > self.similarity_threshold:
                    # print('Matches existing trend - skip')
                    matched = True
                    break
                   
            if not matched:
                # Create new trend
                keywords = self.extract_keywords(cluster_messages)
                trend_id = f"trend_{int(current_time)}_{'_'.join(keywords)}"
                # print(f"New Trend created: {', '.join(keywords)}")
               
                # for m in cluster_messages:
                #     del self.messages[m.id]


                self.trends[trend_id] = Trend(
                    id=trend_id,
                    messages=cluster_messages,
                    keywords=keywords,
                    centroid=cluster_centroid,
                    created_at=current_time,
                    last_update=current_time,
                    locations=set(m.location for m in cluster_messages),
                    original_messages_cnt=len(cluster_messages),
                    matched_messages_cnt=0,
                )

                detected_trends.append(self.trends[trend_id])

        return detected_trends
               
    def update_trend(self, trend_id: str, message: Message):
        trend = self.trends[trend_id]

        # Update centroid
        n = len(trend.messages)  # need to track number of messages
        new_centroid = (trend.centroid * n + message.embedding) / (n + 1)
        
        # trend.centroid = new_centroid
        trend.messages.append(message)
        trend.last_update = message.timestamp
        trend.locations.add(message.location)
        trend.matched_messages_cnt += 1

        # Periodically update keywords
        if len(trend.messages) % 10 == 0:  # every 10 messages
            trend.keywords = self.extract_keywords(trend.messages)

        return []
           
    def extract_keywords(self, messages: List[Message], top_n=5) -> List[str]:
        texts = [m.text for m in messages]
       
        vectorizer = TfidfVectorizer(max_features=100)
        tfidf = vectorizer.fit_transform(texts)
       
        # Get top terms
        importance = np.asarray(tfidf.mean(axis=0)).ravel()
        top_idx = importance.argsort()[-top_n:][::-1]
       
        return [vectorizer.get_feature_names_out()[i] for i in top_idx]
       
    def cleanup_trends(self, current_time: float):
        # Remove expired trends
        expired = []
        for trend_id, trend in self.trends.items():
            if current_time - trend.last_update > self.window_minutes * 60:
                expired.append(trend_id)
               
        for trend_id in expired:
            del self.trends[trend_id]

    def check_matches_cluster_cosine(self, cluster_centroid, msg_embeddings):
        # Reshape to 2D arrays for sklearn
        centroid_2d = np.array(cluster_centroid).reshape(1, -1)
        msg_2d = np.array(msg_embeddings).reshape(1, -1)
        return cosine_similarity(centroid_2d, msg_2d)[0][0]