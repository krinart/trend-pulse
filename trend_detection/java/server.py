import socket
import os
import json
from sentence_transformers import SentenceTransformer
import nltk
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
import re
import numpy as np
from threading import Thread
from queue import Queue
import threading

SOCKET_PATH = os.environ.get('SOCKET_PATH', '/tmp/embedding_server.sock')
MAX_WORKERS = 4  # Adjust based on your CPU cores
request_queue = Queue(maxsize=100)  # Limit queue size to prevent memory issues

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


def process_request(connection):
    try:
        # Receive data with timeout
        connection.settimeout(5.0)  # 5 second timeout
        data = b''
        while True:
            try:
                chunk = connection.recv(4096)
                if not chunk:
                    break
                data += chunk
            except socket.timeout:
                break

        # Parse and process request
        request = json.loads(data.decode('utf-8'))
        text = request.get('text', '')
        processed_text = preprocess_text(text)
        embedding = model.encode([processed_text])[0]

        print(f"original: {text[:50]}, processed: {processed_text[:50]}")

        # Prepare and send response
        response = {
            'original_text': text,
            'processed_text': processed_text,
            'embedding': embedding.tolist()
        }
        connection.sendall(json.dumps(response).encode('utf-8'))

    except Exception as e:
        error_response = {'error': str(e)}
        try:
            connection.sendall(json.dumps(error_response).encode('utf-8'))
        except:
            pass
    finally:
        try:
            connection.close()
        except:
            pass

def worker():
    while True:
        connection = request_queue.get()
        if connection is None:
            break
        process_request(connection)
        request_queue.task_done()

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
    server.listen(MAX_WORKERS * 2)  # Allow more pending connections

    # Start worker threads
    workers = []
    for _ in range(MAX_WORKERS):
        t = Thread(target=worker)
        t.start()
        workers.append(t)

    print(f"Server listening on {SOCKET_PATH} with {MAX_WORKERS} workers")

    try:
        while True:
            connection, _ = server.accept()
            try:
                request_queue.put(connection, block=True, timeout=1)
            except Queue.Full:
                # Queue is full, reject connection
                connection.close()
    except KeyboardInterrupt:
        print("\nShutting down server...")
    finally:
        # Stop workers
        for _ in workers:
            request_queue.put(None)
        for t in workers:
            t.join()
        server.close()
        os.unlink(SOCKET_PATH)

if __name__ == '__main__':
    main()