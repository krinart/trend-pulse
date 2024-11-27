import nltk
# from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
from nltk.stem import WordNetLemmatizer
from nltk.stem.porter import PorterStemmer
import re

# Download required NLTK data
# nltk.download('punkt')
# nltk.download('punkt_tab')
# nltk.download('stopwords')
# nltk.download('wordnet')

stop_words = {"haven't", 'a', 'weren', 'before', 'of', 'itself', 'on', 'his', 'hers', 'couldn', 'ma', 'being', 'shan', 'with', 'those', 'll', 'theirs', 'had', 'any', "you're", 'by', 'was', 'in', 'aren', 'haven', 'an', 'ourselves', 'shouldn', 'what', 'such', 'through', 'm', 'is', 'don', 'he', 'needn', 'then', 'themselves', 'own', 'be', 'while', 'didn', 'are', 'why', 'their', "wouldn't", "couldn't", 'ain', 'not', 'here', 'these', 'up', 'few', 'wouldn', 'the', 'from', 'this', 'hasn', "didn't", 'against', "you'd", 't', "aren't", 'at', 'you', "shan't", 'isn', "shouldn't", 'after', 'over', 'when', 'just', 'were', 'there', 'd', 'we', 'hadn', 'y', 'how', 'has', 'yours', 'under', 'most', 'did', 'because', 'am', 'having', "you've", 'won', 'until', 'have', 'for', 'him', "don't", 'mustn', 'that', 'them', 'herself', "mightn't", 'yourself', 'it', "hadn't", 'all', 'to', 'i', 'been', 'into', 'your', 'as', 'more', 'so', 'myself', "wasn't", 'she', 'himself', 'other', 'doing', "hasn't", 'mightn', "won't", "she's", 'doesn', 'or', 'off', 'below', 'will', 'nor', 'o', 'during', 're', "needn't", 'out', 'yourselves', 'me', 'very', 'but', "mustn't", 'ours', 'whom', 'once', 'and', 'each', 'who', 'than', 'same', 'wasn', 'now', "that'll", 'do', 'further', 'should', "you'll", 'between', 'where', "doesn't", 'some', 'which', 'they', 'can', 'about', 'her', 'our', 'only', 'too', "isn't", 'again', 've', 'its', "it's", 'my', 's', 'does', 'if', 'above', "should've", 'no', 'both', "weren't", 'down'}


def preprocess_text(text):
    """
    Normalize text by:
    1. Converting to lowercase
    2. Removing special characters/punctuation
    3. Tokenizing
    4. Removing stopwords
    5. Removing short words
    """
    # Convert to lowercase
    # nltk.download('stopwords')
    text = text.lower()
    
    # Remove special characters and extra whitespace
    text = re.sub(r'[^\w\s]', '', text)
    text = re.sub(r'\s+', ' ', text).strip()
    
    # Tokenize
    tokens = word_tokenize(text)
    
    # Remove stopwords and short words
    # stop_words = set(stopwords.words('english'))
    tokens = [
        token for token in tokens 
        if token not in stop_words  # Remove stopwords
        and len(token) > 2  # Remove very short words
    ]
    
    # Join tokens back into text
    return ' '.join(tokens)


def advanced_preprocess_text(text):
    """Additional preprocessing options"""
    # Initialize lemmatizer and stemmer
    lemmatizer = WordNetLemmatizer()
    stemmer = PorterStemmer()
    
    # Basic cleaning first
    text = preprocess_text(text)
    tokens = text.split()
    
    # Lemmatization (converts words to base form)
    # tokens = [lemmatizer.lemmatize(token) for token in tokens]
    
    # Or Stemming (more aggressive, can make words unreadable)
    # tokens = [stemmer.stem(token) for token in tokens]
    
    # Remove numeric tokens
    tokens = [token for token in tokens if not token.isnumeric()]
    
    # Remove domain-specific stopwords
    domain_stopwords = {'custom', 'words', 'to', 'remove'}
    tokens = [token for token in tokens if token not in domain_stopwords]
    
    return ' '.join(tokens)