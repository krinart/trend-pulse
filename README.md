# Trend-Pulse: Social Media Trend Detection System

A demonstration platform showcasing real-time trend detection from social media streams using AI-based analysis techniques.

## Overview

Trend-Pulse processes streams of social media posts to identify emerging trends based on semantic similarity and message volume. The system performs multi-level trend analysis, detecting both local trends (specific to geographic areas) and global trends (spanning multiple locations).

## Key Features

- **Real-time Stream Processing**: Consumes and analyzes social media posts as they arrive
- **AI-Powered Analysis**: Uses vector embeddings to capture semantic meaning of messages
- **Hierarchical Trend Detection**: Identifies both local and global trends
- **Geographic Analysis**: Associates trends with specific locations
- **Interactive Visualization**: Displays trends on maps with detailed information
- **Scalable Architecture**: Designed for deployment on Kubernetes/Docker

## Architecture

The system consists of several key components:

### Stream Generation
- Simulates social media posts with text content, timestamp, location, and topic data
- Located in `/stream_generation` and `/gen_lib` directories

### Trend Detection
- **Local Trend Processing**:
  - Converts message text to vector embeddings
  - Clusters messages using cosine similarity
  - Activates trends when similar messages accumulate
  - Tracks message volume and statistics over time
  - Emits trend events (activation, stats, deactivation)

- **Global Trend Processing**:
  - Combines similar local trends from different locations
  - Creates global trends when patterns appear in multiple areas
  - Aggregates statistics and maintains trend relationships
  - Emits global trend events

### Visualization UI
- Angular-based web application displaying trends on interactive maps
- Shows both local and global trends with associated metadata
- Located in `/ui` directory

## Technology Stack

- **Stream Processing**: Apache Flink
- **Vector Embeddings**: Python-based embedding service
- **Frontend**: Angular with MapBox integration
- **Deployment**: Docker, Kubernetes (AKS support)

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 11+
- Python 3.7+
- Node.js 14+

### Running Locally
1. Start the trend detection services:
   ```
   cd trend_detection
   docker-compose up
   ```

2. Run the UI application:
   ```
   cd ui
   npm install
   npm start
   ```

3. Generate sample data streams:
   ```
   cd stream_generation
   python azure_generator.py
   ```

## Deployment

The application supports deployment to Kubernetes with configuration files provided in the `/trend_detection/aks` directory.

## Development

Development notebooks for exploring trend generation, detection, and merging are available in the `/notebooks` directory.