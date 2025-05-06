FROM openjdk:21-jdk-slim as base


RUN apt-get update && apt-get install -y python3 python3-pip python3-venv && apt-get clean
WORKDIR /app

RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --upgrade pip

RUN pip install grpcio
RUN pip install grpcio-tools
RUN pip install sentence-transformers
RUN pip install numpy
WORKDIR /app

# Set working dir
WORKDIR /app

# Copy Java application
COPY target/search-gateway-api-1.0-SNAPSHOT.jar /app/search-gateway-api.jar

COPY python/embedding_server.py /app/embedding_server.py
COPY python/embedding_service_pb2.py /app/embedding_service_pb2.py
COPY python/embedding_service_pb2_grpc.py /app/embedding_service_pb2_grpc.py

EXPOSE 8080

CMD ["sh", "-c", "\
   python3 embedding_server.py & \
   java -jar search-gateway-api.jar"]