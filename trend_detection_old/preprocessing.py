import nltk
from nltk.corpus import stopwords
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
whitelist = {'ai', 'ml', 'ui', 'ux', 'os', 'tv', 'uk', 'us', 'eu'}
    

def preprocess_text(text):
    # Convert to lowercase
    text = text.lower()
    
    # Save whitelisted terms by replacing them with placeholders
    preserved_terms = {}
    for i, term in enumerate(whitelist):
        placeholder = f"PRESERVED_{i}"
        if term in text.lower():
            preserved_terms[placeholder] = term
            text = re.sub(rf'\b{term}\b', placeholder, text, flags=re.IGNORECASE)
    
    # Regular preprocessing
    text = re.sub(r'[^\w\s]', '', text)
    text = re.sub(r'\s+', ' ', text).strip()
    
    tokens = word_tokenize(text)
    
    tokens = [
        token for token in tokens 
        if (token not in stop_words and len(token) > 2) or 
           token in whitelist or 
           token in preserved_terms.keys()
    ]
    
    # Restore preserved terms
    processed_text = ' '.join(tokens)
    for placeholder, term in preserved_terms.items():
        processed_text = processed_text.replace(placeholder, term)
    
    return processed_text


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