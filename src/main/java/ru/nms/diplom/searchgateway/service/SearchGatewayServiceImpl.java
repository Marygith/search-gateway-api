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

    public SearchGatewayServiceImpl(ClusterStateClient clusterStateClient) {
        this.clusterStateClient = clusterStateClient;
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        ShardsDistributionResponse state = clusterStateClient.getShardsDistribution();
        List<ShardGroup> shardGroups = ShardUtils.groupShardsByIpAndType(state);

        int totalGroups = shardGroups.size();
        int adjustedK = Math.max(1, (int) Math.ceil((double) request.getK() / totalGroups * 1.5));

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

            stub.shardSearch(shardRequest, new ShardSearchObserver(
                    allDocs, completed, totalGroups, request.getK(), responseObserver));
        }
    }
}
