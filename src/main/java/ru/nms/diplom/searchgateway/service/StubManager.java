package ru.nms.diplom.searchgateway.service;

import embedding.EmbeddingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StubManager {
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceStub> stubPool = new ConcurrentHashMap<>();
    private static final int BASE_PORT = 9000;
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceBlockingStub> blockingStubPool = new ConcurrentHashMap<>();

    private static final List<EmbeddingServiceGrpc.EmbeddingServiceFutureStub> stubs = new ArrayList<>();
    private static final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    static {
        int count = 4; // default
        String envVar = System.getenv("EMBEDDING_SERVER_COUNT");
        if (envVar != null) {
            try {
                count = Integer.parseInt(envVar);
            } catch (NumberFormatException e) {
                System.err.println("Invalid EMBEDDING_SERVER_COUNT value, defaulting to 4");
            }
        }

        for (int i = 0; i < count; i++) {
            int port = BASE_PORT + i;
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            stubs.add(EmbeddingServiceGrpc.newFutureStub(channel));
            System.out.printf("Initialized embedding stub for localhost:%d%n", port);
        }
    }

    public static EmbeddingServiceGrpc.EmbeddingServiceFutureStub getEmbeddingFutureStub() {
        int index = roundRobinCounter.getAndUpdate(i -> (i + 1) % stubs.size());
        return stubs.get(index);
    }
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
}