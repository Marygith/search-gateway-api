package ru.nms.diplom.searchgateway.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StubManager {
    private static final Map<String, ShardSearchServiceGrpc.ShardSearchServiceStub> stubPool = new ConcurrentHashMap<>();

    public static ShardSearchServiceGrpc.ShardSearchServiceStub getBaseStub(String ip) {
        return stubPool.computeIfAbsent(ip, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(address, 9090)
                    .usePlaintext()
                    .build();
            return ShardSearchServiceGrpc.newStub(channel); // base stub
        });
    }
}