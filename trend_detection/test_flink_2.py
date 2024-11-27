import nltk
from nltk.sentiment import SentimentIntensityAnalyzer
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.functions import MapFunction
import sys

# Download NLTK data required for sentiment analysis
nltk.download('vader_lexicon')

# Initialize the Sentiment Analyzer
sia = SentimentIntensityAnalyzer()

# Define the custom MapFunction for sentiment analysis
class SentimentAnalysis(MapFunction):
    def map(self, value):
        # Compute the sentiment score for the given text
        sentiment_score = sia.polarity_scores(value)['compound']
        return f'Text: {value}, Sentiment Score: {sentiment_score}'

def main():
    # Set up the Flink streaming environment
    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_parallelism(1)  # Optional: Set parallelism for debugging

    # Create a sample data source with text messages
    data_source = env.from_collection([
        "This product is fantastic!",
        "I really didn't like this experience.",
        "The service was okay, nothing special.",
        "Absolutely terrible, would not recommend."
    ])

    # Apply the sentiment analysis function
    sentiment_stream = data_source.map(SentimentAnalysis())

    # Print the results
    sentiment_stream.print()

    # Execute the Flink pipeline
    env.execute("Sentiment Analysis with Apache Flink")


if __name__ == '__main__':
    main()