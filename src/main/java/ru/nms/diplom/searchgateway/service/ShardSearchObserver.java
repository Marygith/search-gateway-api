package ru.nms.diplom.searchgateway.service;

import io.grpc.stub.StreamObserver;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Comparator;
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
    private final List<ru.nms.diplom.shardsearch.Document> allDocs;
    private final AtomicInteger completed;
    private final int totalGroups;
    private final int originalK;
    private final float shardLimitCoefficient;
    private final int adjustedK;
    private final StreamObserver<SearchResponse> responseObserver;
    private final float p = 5.4246f;
    private final float w = 0.7427f;
//    private final List<Document> docs = new ArrayList<>();

    public ShardSearchObserver(String host, List<Integer> shardIds, int indexType, List<ru.nms.diplom.shardsearch.Document> allDocs,
                               AtomicInteger completed, int totalGroups, int originalK, float shardLimitCoefficient,
                               int adjustedK, StreamObserver<SearchResponse> responseObserver) {
        this.host = host;
        this.shardIds = shardIds;
        this.indexType = indexType;
        this.allDocs = allDocs;
        this.completed = completed;
        this.totalGroups = totalGroups;
        this.originalK = originalK;
        this.responseObserver = responseObserver;
        this.shardLimitCoefficient = shardLimitCoefficient;
        this.adjustedK = adjustedK;
    }

    @Override
    public void onNext(ShardSearchResponse value) {
//        System.out.printf("came successful response with %s docs%n", value.getResultsCount());
        allDocs.addAll(value.getResultsList());
//        System.out.println("came response with docs: " + value.getResultsList().stream().map(ru.nms.diplom.shardsearch.Document::getId).toList());
//        for (ru.nms.diplom.shardsearch.Document doc : value.getResultsList()) {
//            docs.add(Document.newBuilder()
//                    .setId(doc.getId())
//                    .setFaissScore(doc.getFaissScore())
//                    .setLuceneScore(doc.getLuceneScore())
//                    .build());
//        }
    }

    @Override
    public void onError(Throwable t) {
        System.out.printf("came error from host %s, requested shard ids: %s, index type: %s, error: %s%n", host, shardIds,
            indexType == 0 ? "LUCENE" : "FAISS", t);
        maybeRespond();
    }

    @Override
    public void onCompleted() {
//        allDocs.addAll(docs);
        maybeRespond();
    }

    private void maybeRespond() {
        if (completed.incrementAndGet() == totalGroups) {
//            System.out.println("all responses came!");
            var docIds = new IntOpenHashSet(allDocs.size());
            List<ru.nms.diplom.shardsearch.Document> result = new ArrayList<>();
            for (var doc : allDocs) {
                if (docIds.add(doc.getId())) {
                    result.add(doc);
                }
            }
            responseObserver.onNext(SearchResponse.newBuilder().addAllIds(getFinalResult(result)).setMeta(createMeta()).build());
            responseObserver.onCompleted();
        }
    }

    private Meta createMeta() {
        return Meta.newBuilder().setAdjustedK(adjustedK).build();
    }


    private List<Integer> getFinalResult(List<ru.nms.diplom.shardsearch.Document> docs) {
        float maxLuceneScore = docs.stream().map(ru.nms.diplom.shardsearch.Document::getLuceneScore).max(Float::compare).get();
        float minLuceneScore = docs.stream().map(ru.nms.diplom.shardsearch.Document::getLuceneScore).min(Float::compare).get();

        float maxFaissDistance = docs.stream().map(ru.nms.diplom.shardsearch.Document::getFaissScore).max(Float::compare).get();
        float minFaissDistance = docs.stream().map(ru.nms.diplom.shardsearch.Document::getFaissScore).min(Float::compare).get();

        return docs.stream()
            .map(d -> d.toBuilder()
                .setFaissScore(1 - (d.getFaissScore() - minFaissDistance) / (maxFaissDistance - minFaissDistance))
                .setLuceneScore((d.getLuceneScore() - minLuceneScore) / (maxLuceneScore - minLuceneScore)))
            .sorted(Comparator.comparingDouble(d -> -(p * d.getFaissScore() + w * d.getLuceneScore())))
            .map(ru.nms.diplom.shardsearch.Document.Builder::getId)
            .limit(originalK)
            .toList();
    }

//    public List<Document> normalizeScores(List<Document> data) {
//
//        float maxLuceneScore = data.stream().map(Document::getLuceneScore).max(Float::compare).get();
//        float minLuceneScore = data.stream().map(Document::getLuceneScore).min(Float::compare).get();
//
//        float maxFaissDistance = data.stream().map(Document::getFaissScore).max(Float::compare).get();
//        float minFaissDistance = data.stream().map(Document::getFaissScore).min(Float::compare).get();
//
//        var result = new ArrayList<Document>();
//        data.forEach(d -> result.add(d.toBuilder()
//            .setFaissScore(1 - (d.getFaissScore() - minFaissDistance) / (maxFaissDistance - minFaissDistance))
//            .setLuceneScore((d.getLuceneScore() - minLuceneScore) / (maxLuceneScore - minLuceneScore))
//            .build()
//        ));
//        return result;
//    }
}
