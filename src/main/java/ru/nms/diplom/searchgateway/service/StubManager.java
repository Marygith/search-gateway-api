package ru.nms.diplom.searchgateway.service;

import embedding.EmbeddingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StubManager {
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceStub> stubPool = new ConcurrentHashMap<>();
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceBlockingStub> blockingStubPool = new ConcurrentHashMap<>();
    private static final EmbeddingServiceGrpc.EmbeddingServiceFutureStub embeddingFutureStub = initEmbeddingFutureStub();

    public static ShardSearchServiceGrpc.ShardSearchServiceStub getBaseStub(String host, int port) {
        return stubPool.computeIfAbsent(host + port, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .maxInboundMessageSize(10000000)
                    .build();
            return ShardSearchServiceGrpc.newStub(channel); // base stub
        });
    }

    public static ShardSearchServiceGrpc.ShardSearchServiceBlockingStub getBlockingStub(String host, int port) {
        return blockingStubPool.computeIfAbsent(host + port, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(10000000)
                .build();
            return ShardSearchServiceGrpc.newBlockingStub(channel); // base stub
        });
    }
    public static EmbeddingServiceGrpc.EmbeddingServiceFutureStub initEmbeddingFutureStub() {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                    .usePlaintext()
                    .build();
            return EmbeddingServiceGrpc.newFutureStub(channel);
    }

    public static EmbeddingServiceGrpc.EmbeddingServiceFutureStub getEmbeddingFutureStub() {
        return embeddingFutureStub;
    }
}