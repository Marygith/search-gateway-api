import grpc
from concurrent import futures
import time
import os
import numpy as np
from sentence_transformers import SentenceTransformer
import embedding_service_pb2
import embedding_service_pb2_grpc
from multiprocessing import Process

class EmbeddingService(embedding_service_pb2_grpc.EmbeddingServiceServicer):
    def __init__(self):
        print("Loading embedding model...")
        self.model = SentenceTransformer("all-MiniLM-L6-v2")
        print("Model loaded.")

    def Encode(self, request, context):
        query = request.query
        embedding = self.model.encode([query])[0].astype(np.float32)
        return embedding_service_pb2.EncodedVector(values=embedding.tolist())

def run_server(port):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    embedding_service_pb2_grpc.add_EmbeddingServiceServicer_to_server(EmbeddingService(), server)
    server.add_insecure_port(f"[::]:{port}")
    print(f"EmbeddingService running on port {port}")
    server.start()
    try:
        while True:
            time.sleep(86400)
    except KeyboardInterrupt:
        server.stop(0)

if __name__ == "__main__":
    num_servers = int(os.getenv("EMBEDDING_SERVER_COUNT", "4"))
    base_port = 9000

    processes = []
    for i in range(num_servers):
        port = base_port + i
        p = Process(target=run_server, args=(port,))
        p.start()
        processes.append(p)

    for p in processes:
        p.join()
