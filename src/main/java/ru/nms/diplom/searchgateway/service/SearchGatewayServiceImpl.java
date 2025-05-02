package ru.nms.diplom.searchgateway.service;

import io.grpc.stub.StreamObserver;
import ru.nms.diplom.clusterstate.service.ShardsDistributionResponse;
import ru.nms.diplom.searchgateway.client.ClusterStateClient;
import ru.nms.diplom.searchgateway.model.ShardGroup;
import ru.nms.diplom.searchgateway.util.ShardUtils;
import ru.nms.diplom.shardsearch.ShardSearchRequest;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchGatewayServiceImpl extends SearchGatewayServiceGrpc.SearchGatewayServiceImplBase {

    private final ClusterStateClient clusterStateClient;
    private final ShardsDistributionResponse state;

    public SearchGatewayServiceImpl(ClusterStateClient clusterStateClient) {
        this.clusterStateClient = clusterStateClient;
        state = clusterStateClient.getShardsDistribution();
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
//        ShardsDistributionResponse state = clusterStateClient.getShardsDistribution();
        List<ShardGroup> shardGroups = ShardUtils.groupShardsByIpAndType(state);

        int totalGroups = shardGroups.size();
        int adjustedK = adjustK(request.getK(),  request.getShardLimitCoefficient(), request.getIsCoefficientAbsolute());

        List<Document> allDocs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);

        for (ShardGroup group : shardGroups) {
            ShardSearchRequest shardRequest = ShardSearchRequest.newBuilder()
                    .setQuery(request.getQuery())
                    .setK(adjustedK)

                    .setIndexType(group.indexType)
                    .addAllShardIds(group.shardIds)
                    .build();

            ShardSearchServiceGrpc.ShardSearchServiceStub stub = StubManager.getBaseStub(group.host, group.port).withDeadlineAfter(20, TimeUnit.SECONDS);

            System.out.printf("sending request to %s, index type: %s, shard ids: %s, request: %s%n", group.host + ":" + group.port, group.indexType == 0 ? "LUCENE" : "FAISS", group.shardIds, request);
            stub.shardSearch(shardRequest, new ShardSearchObserver(group.host, group.shardIds, group.indexType,
                    allDocs, completed, totalGroups, request.getK(), request.getShardLimitCoefficient(), request.getIsCoefficientAbsolute(), adjustedK, responseObserver));
        }
    }

    private int adjustK(int k, float coefficient, boolean isCoefficientAbsolute) {
        int amountOfShards = 3;
        if (coefficient < 0.0001) {
//            System.out.println("coefficient is too small, preserving original k");
            return k;
        }
        if (isCoefficientAbsolute) {
            return (int) Math.ceil((double) k * coefficient);
        } else {

                var result = (int) Math.ceil((double) k * coefficient / amountOfShards);
//                System.out.println("k is %s, coefficient is %s, amount of shards is %s, adjusted k is: %s".formatted(k, coefficient, amountOfShards, result));
                return result;

        }
    }
}
