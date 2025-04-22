package ru.nms.diplom.searchgateway.service;

import io.grpc.stub.StreamObserver;
import ru.nms.diplom.shardsearch.ShardSearchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ShardSearchObserver implements StreamObserver<ShardSearchResponse> {
    private final List<Document> allDocs;
    private final AtomicInteger completed;
    private final int totalGroups;
    private final int originalK;
    private final StreamObserver<SearchResponse> responseObserver;
    private final List<Document> docs = new ArrayList<>();

    public ShardSearchObserver(List<Document> allDocs, AtomicInteger completed, int totalGroups,
                               int originalK, StreamObserver<SearchResponse> responseObserver) {
        this.allDocs = allDocs;
        this.completed = completed;
        this.totalGroups = totalGroups;
        this.originalK = originalK;
        this.responseObserver = responseObserver;
    }

    @Override
    public void onNext(ShardSearchResponse value) {
        for (ru.nms.diplom.shardsearch.Document doc : value.getResultsList()) {
            docs.add(Document.newBuilder()
                    .setId((int) doc.getId())
                    .setFaissScore(doc.getFaissScore())
                    .setLuceneScore(doc.getLuceneScore())
                    .build());
        }
    }

    @Override
    public void onError(Throwable t) {
        maybeRespond();
    }

    @Override
    public void onCompleted() {
        allDocs.addAll(docs);
        maybeRespond();
    }

    private void maybeRespond() {
        if (completed.incrementAndGet() == totalGroups) {
//            List<Document> topK = allDocs.stream()
//                    .sorted(Comparator.comparingDouble(doc -> -(doc.getFaissScore() + doc.getLuceneScore())))
//                    .limit(originalK)
//                    .toList();

            responseObserver.onNext(SearchResponse.newBuilder().addAllResults(allDocs).build());
            responseObserver.onCompleted();
        }
    }
}
