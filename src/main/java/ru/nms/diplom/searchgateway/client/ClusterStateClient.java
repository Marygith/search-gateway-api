package ru.nms.diplom.searchgateway.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.clusterstate.service.ShardServiceGrpc;
import ru.nms.diplom.clusterstate.service.ShardsDistributionResponse;

public class ClusterStateClient {
    private final ShardServiceGrpc.ShardServiceBlockingStub stub;

    public ClusterStateClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = ShardServiceGrpc.newBlockingStub(channel);
    }

    public ShardsDistributionResponse getShardsDistribution() {
        return stub.getShardsDistribution(com.google.protobuf.Empty.getDefaultInstance());
    }
}
