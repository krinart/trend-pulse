FROM flink:1.17.1-scala_2.12-java11

# Install Python, pip, virtualenv and JDK
RUN apt-get update -y && \
    apt-get install -y python3 python3-pip python3-dev python3-venv openjdk-11-jdk && \
    ln -s /usr/bin/python3 /usr/bin/python

# Find and set correct JAVA_HOME for ARM
RUN JAVA_PATH=$(readlink -f /usr/bin/java | sed 's:/bin/java::') && \
    echo "export JAVA_HOME=$JAVA_PATH" >> /etc/profile && \
    echo "export JAVA_HOME=$JAVA_PATH" >> ~/.bashrc && \
    export JAVA_HOME=$JAVA_PATH

# Create and activate virtual environment
ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

# Install Apache Flink Python API and other dependencies in virtualenv
RUN . /etc/profile && \
    pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir numpy==1.24.4 && \
    pip install --no-cache-dir pandas==1.3.5 && \
    pip install --no-cache-dir apache-beam==2.48.0 && \
    pip install --no-cache-dir apache-flink==1.20.0 && \
    pip install --no-cache-dir nltk==3.9.1 && \
    pip install --no-cache-dir sentence-transformers==2.2.2 && \
    python -c "import nltk; nltk.download('punkt'); nltk.download('stopwords'); nltk.download('punkt_tab'); nltk.download('wordnet')"

WORKDIR /opt/flink/work
COPY test_flink.py .

# Make sure we use the virtualenv by default
ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"