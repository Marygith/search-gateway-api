package ru.nms.diplom.searchgateway.service;

import embedding.EmbeddingServiceOuterClass;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import ru.nms.diplom.clusterstate.service.ShardsDistributionResponse;
import ru.nms.diplom.searchgateway.client.ClusterStateClient;
import ru.nms.diplom.searchgateway.model.ShardGroup;
import ru.nms.diplom.searchgateway.util.ShardUtils;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.SearchDocsRequest;
import ru.nms.diplom.shardsearch.SearchDocsResponse;
import ru.nms.diplom.shardsearch.ShardSearchRequest;
import ru.nms.diplom.shardsearch.ShardSearchResponse;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ru.nms.diplom.shardsearch.SimilarityScoresRequest;

public class SearchGatewayServiceImpl extends SearchGatewayServiceGrpc.SearchGatewayServiceImplBase {

    private final ClusterStateClient clusterStateClient;
    private final int amountOfShards = Integer.parseInt(System.getenv("SHARDS_AMOUNT"));;

    public SearchGatewayServiceImpl(ClusterStateClient clusterStateClient) {
        this.clusterStateClient = clusterStateClient;
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        if (request.getUseIndexesSeparately()) {
            hybridSearch(request, responseObserver);
        } else {
            jointSearch(request, responseObserver);
        }
    }

    public void jointSearch(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        var encodedQueryFuture = StubManager.getEmbeddingFutureStub().encode(EmbeddingServiceOuterClass.EncodeRequest.newBuilder()
            .setQuery(request.getQuery())
            .build());
        ShardsDistributionResponse state = clusterStateClient.getShardsDistribution();
        List<ShardGroup> shardGroups = ShardUtils.groupShardsByIpAndType(state);

        int totalGroups = shardGroups.size();
        int adjustedK = adjustK(request.getK(),  request.getShardLimitCoefficient());

        List<ru.nms.diplom.shardsearch.Document> allDocs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);
        List<Float> encodedQuery = null;
        try {
            encodedQuery = encodedQueryFuture.get().getValuesList();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("failed to create embedding");
            responseObserver.onError(e);
            return;
        }
        for (ShardGroup group : shardGroups) {
            ShardSearchRequest shardRequest = ShardSearchRequest.newBuilder()
                .setQuery(request.getQuery())
                .setK(adjustedK)
                .addAllEncodedQuery(encodedQuery)
                .setIndexType(group.indexType)
                .addAllShardIds(group.shardIds)
                .build();

            ShardSearchServiceGrpc.ShardSearchServiceStub stub = StubManager.getBaseStub(group.host, group.port).withDeadlineAfter(2, TimeUnit.SECONDS);

//            System.out.printf("sending request to %s, index type: %s, shard ids: %s, request: %s%n", group.host + ":" + group.port, group.indexType == 0 ? "LUCENE" : "FAISS", group.shardIds, request);
            stub.shardSearch(shardRequest, new ShardSearchObserver(group.host, group.shardIds, group.indexType,
                allDocs, completed, totalGroups, request.getK(), request.getShardLimitCoefficient(), adjustedK, responseObserver));
        }
    }
    private int adjustK(int k, float coefficient) {
        if (coefficient < 0.0001) {
            return k;
        }
        return (int) Math.ceil((double) k * coefficient / amountOfShards);
    }

    public void hybridSearch(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        var encodedQueryFuture = StubManager.getEmbeddingFutureStub().encode(
            EmbeddingServiceOuterClass.EncodeRequest.newBuilder().setQuery(request.getQuery()).build()
        );

        ShardsDistributionResponse state = clusterStateClient.getShardsDistribution();
        List<ShardGroup> shardGroups = ShardUtils.groupShardsByIpAndType(state);

        int totalShards = (int) shardGroups.stream().flatMap(g -> g.shardIds.stream()).distinct().count();
        int adjustedK = adjustK(request.getK(), request.getShardLimitCoefficient());

        List<Float> encodedQuery;
        try {
            encodedQuery = encodedQueryFuture.get().getValuesList();
        } catch (Exception e) {
            responseObserver.onError(e);
            return;
        }

        Map<Integer, Document.Builder> aggregatedDocs = new ConcurrentHashMap<>();
        AtomicInteger pendingSearchGroups = new AtomicInteger(shardGroups.size());

        // Выполнение searchDocs по каждой группе
        for (ShardGroup group : shardGroups) {
            SearchDocsRequest docsRequest = SearchDocsRequest.newBuilder()
                .setQuery(request.getQuery())
                .setK(adjustedK)
                .setIndexType(group.indexType)
                .addAllEncodedQuery(encodedQuery)
                .addAllShardIds(group.shardIds)
                .build();

            var stub = StubManager.getBlockingStub(group.host, group.port);

            CompletableFuture.runAsync(() -> {
                try {
                    SearchDocsResponse docsResponse = stub.searchDocs(docsRequest);
                    for (ru.nms.diplom.shardsearch.Document doc : docsResponse.getDocumentsList()) {
                        aggregatedDocs.compute(doc.getId(), (id, builder) -> {
                            if (builder == null) builder = ru.nms.diplom.shardsearch.Document.newBuilder().setId(id);
                            if (group.indexType == 0) {
                                builder.setFaissScore(doc.getFaissScore());
                            } else {
                                builder.setLuceneScore(doc.getLuceneScore());
                            }
                            return builder;
                        });
                    }
                } catch (Exception e) {
                    System.err.printf("Failed searchDocs for %s:%d - %s%n", group.host, group.port, e.getMessage());
                } finally {
                    if (pendingSearchGroups.decrementAndGet() == 0) {
                        continueWithMissingScores(aggregatedDocs, shardGroups, request, encodedQuery, totalShards, responseObserver);
                    }
                }
            });
        }
    }

    private void continueWithMissingScores(
        Map<Integer, ru.nms.diplom.shardsearch.Document.Builder> aggregatedDocs,
        List<ShardGroup> shardGroups,
        SearchRequest request,
        List<Float> encodedQuery,
        int totalShards,
        StreamObserver<SearchResponse> responseObserver
    ) {
        List<CompletableFuture<Void>> enrichmentTasks = new ArrayList<>();

        for (Map.Entry<Integer, ru.nms.diplom.shardsearch.Document.Builder> entry : aggregatedDocs.entrySet()) {
            int docId = entry.getKey();
            ru.nms.diplom.shardsearch.Document.Builder docBuilder = entry.getValue();

            // Пропустить, если оценки обе есть
            if (docBuilder.getLuceneScore() > 0 && docBuilder.getFaissScore() > 0) continue;

            int shardId = docId % totalShards;
            int missingIndexType = docBuilder.getFaissScore() > 0 ? 0 : 1;

            ShardGroup targetGroup = shardGroups.stream()
                .filter(g -> g.indexType == missingIndexType && g.shardIds.contains(shardId))
                .findFirst()
                .orElse(null);

            if (targetGroup == null) continue;

            SimilarityScoresRequest scoreRequest = SimilarityScoresRequest.newBuilder()
                .setShardId(shardId)
                .setIndexType(missingIndexType)
                .setQuery(request.getQuery())
                .addAllEncodedQuery(encodedQuery)
                .addDocuments(docBuilder.build())
                .build();

            var stub = StubManager.getBlockingStub(targetGroup.host, targetGroup.port);
            enrichmentTasks.add(CompletableFuture.runAsync(() -> {
                try {
                    ShardSearchResponse response = stub.getSimilarityScores(scoreRequest);
                    if (!response.getResultsList().isEmpty()) {
                        ru.nms.diplom.shardsearch.Document enriched = response.getResults(0);
                        synchronized (docBuilder) {
                            if (missingIndexType == 0) {
                                docBuilder.setLuceneScore(enriched.getLuceneScore());
                            } else {
                                docBuilder.setFaissScore(enriched.getFaissScore());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.printf("Enrichment failed for doc %d: %s%n", docId, e.getMessage());
                }
            }));
        }

        // Дождаться всех задач и вернуть результат
        CompletableFuture.allOf(enrichmentTasks.toArray(new CompletableFuture[0]))
            .whenComplete((ignored, throwable) -> {
                responseObserver.onNext(SearchResponse.newBuilder().addAllIds(ScoringUtils.getFinalResult(aggregatedDocs.values(), request.getK())).build());
                responseObserver.onCompleted();
            });
    }
}
