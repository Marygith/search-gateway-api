import grpc
from concurrent import futures
import time
import numpy as np
from sentence_transformers import SentenceTransformer
import embedding_service_pb2
import embedding_service_pb2_grpc

class EmbeddingService(embedding_service_pb2_grpc.EmbeddingServiceServicer):
    def __init__(self):
        print("Loading embedding model...")
        self.model = SentenceTransformer("all-MiniLM-L6-v2")
        print("Model loaded.")

    def Encode(self, request, context):
        query = request.query
        embedding = self.model.encode([query])[0].astype(np.float32)
        return embedding_service_pb2.EncodedVector(values=embedding.tolist())

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    embedding_service_pb2_grpc.add_EmbeddingServiceServicer_to_server(EmbeddingService(), server)
    server.add_insecure_port("[::]:9000")
    print("EmbeddingService running on port 9000")
    server.start()
    try:
        while True:
            time.sleep(86400)
    except KeyboardInterrupt:
        server.stop(0)

if __name__ == "__main__":
    serve()
