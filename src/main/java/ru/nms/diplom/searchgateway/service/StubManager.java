package ru.nms.diplom.searchgateway.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StubManager {
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceStub> stubPool = new ConcurrentHashMap<>();

    public static ShardSearchServiceGrpc.ShardSearchServiceStub getBaseStub(String host, int port) {
        return stubPool.computeIfAbsent(host + port, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .maxInboundMessageSize(10000000)
                    .build();
            return ShardSearchServiceGrpc.newStub(channel); // base stub
        });
    }
}