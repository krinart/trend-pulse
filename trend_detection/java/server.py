import socket
import os
import json
from sentence_transformers import SentenceTransformer
import nltk
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
import re
import numpy as np

SOCKET_PATH = os.environ.get('SOCKET_PATH', '/tmp/embedding_server.sock')

# Initialize the model globally
model = SentenceTransformer('all-MiniLM-L6-v2')

stop_words = set(stopwords.words('english'))

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


def handle_client(connection):
    try:
        # Receive data
        data = b''
        while True:
            chunk = connection.recv(4096)
            if not chunk:
                break
            data += chunk

        # Parse JSON
        request = json.loads(data.decode('utf-8'))
        text = request.get('text', '')

        # Process text
        processed_text = preprocess_text(text)
        embedding = model.encode([processed_text])[0]

        print(f"text: {text[:70]}, processed_text: {processed_text[:70]}")

        # Prepare response
        response = {
            'original_text': text,
            'processed_text': processed_text,
            'embedding': embedding.tolist()
        }

        # Send response
        connection.sendall(json.dumps(response).encode('utf-8'))

    except Exception as e:
        error_response = {'error': str(e)}
        connection.sendall(json.dumps(error_response).encode('utf-8'))
    finally:
        connection.close()

def main():
    # Download NLTK data if needed
    try:
        nltk.data.find('tokenizers/punkt')
    except LookupError:
        nltk.download('punkt')

    # Remove existing socket file if it exists
    try:
        os.unlink(SOCKET_PATH)
    except OSError:
        if os.path.exists(SOCKET_PATH):
            raise

    # Create Unix domain socket
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(SOCKET_PATH)
    server.listen(1)

    print(f"Server listening on {SOCKET_PATH}")

    try:
        while True:
            connection, client_address = server.accept()
            handle_client(connection)
    except KeyboardInterrupt:
        print("\nShutting down server...")
    finally:
        server.close()
        os.unlink(SOCKET_PATH)


if __name__ == '__main__':
    main()