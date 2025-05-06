package ru.nms.diplom.searchgateway.service;

import io.grpc.stub.StreamObserver;
import ru.nms.diplom.shardsearch.ShardSearchResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ShardSearchObserver implements StreamObserver<ShardSearchResponse> {
    private final String host;
    private final List<Integer> shardIds;
    private final int indexType;
    private final List<Document> allDocs;
    private final AtomicInteger completed;
    private final int totalGroups;
    private final int originalK;
    private final float shardLimitCoefficient;
    private final boolean isCoefficientAbsolute;
    private final int adjustedK;
    private final StreamObserver<SearchResponse> responseObserver;
    private final List<Document> docs = new ArrayList<>();

    public ShardSearchObserver(String host, List<Integer> shardIds, int indexType, List<Document> allDocs, AtomicInteger completed, int totalGroups, int originalK, float shardLimitCoefficient,
                               boolean isCoefficientAbsolute, int adjustedK, StreamObserver<SearchResponse> responseObserver) {
        this.host = host;
        this.shardIds = shardIds;
        this.indexType = indexType;
        this.allDocs = allDocs;
        this.completed = completed;
        this.totalGroups = totalGroups;
        this.originalK = originalK;
        this.responseObserver = responseObserver;
        this.shardLimitCoefficient = shardLimitCoefficient;
        this.isCoefficientAbsolute = isCoefficientAbsolute;
        this.adjustedK = adjustedK;
    }

    @Override
    public void onNext(ShardSearchResponse value) {
        System.out.printf("came successful response with %s docs%n", value.getResultsCount());
//        System.out.println("came response with docs: " + value.getResultsList().stream().map(ru.nms.diplom.shardsearch.Document::getId).toList());
        for (ru.nms.diplom.shardsearch.Document doc : value.getResultsList()) {
            docs.add(Document.newBuilder()
                    .setId(doc.getId())
                    .setFaissScore(doc.getFaissScore())
                    .setLuceneScore(doc.getLuceneScore())
                    .build());
        }
    }

    @Override
    public void onError(Throwable t) {
        System.out.printf("came error from host %s, requested shard ids: %s, index type: %s, error: %s%n", host, shardIds, indexType == 0 ? "LUCENE" : "FAISS", t);
        maybeRespond();
    }

    @Override
    public void onCompleted() {
        allDocs.addAll(docs);
        maybeRespond();
    }

    private void maybeRespond() {
        if (completed.incrementAndGet() == totalGroups) {
//            System.out.println("all responses came!");
            Set<Integer> docIds = new HashSet<>();
            List<Document> result = new ArrayList<>();
            for (var doc: allDocs) {
                if (docIds.add(doc.getId())) {
                    result.add(doc);
                }
            }

            responseObserver.onNext(SearchResponse.newBuilder().addAllResults(result).setMeta(createMeta()).build());
            responseObserver.onCompleted();
        }
    }

    private Meta createMeta() {
        return Meta.newBuilder().setAdjustedK(adjustedK).setAmountOfReceivedDocs(allDocs.size()).build();
    }
}
