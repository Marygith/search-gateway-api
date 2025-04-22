package ru.nms.diplom.searchgateway.client;

import ru.nms.diplom.searchgateway.service.Document;
import ru.nms.diplom.searchgateway.service.StubManager;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ShardSearchClient {
//    public List<Document> searchShard(String ip, int shard, String type, String query, int adjustedK) {
//        // Placeholder: call FAISS/Lucene shard via gRPC and return top `adjustedK` results
//        // This would require you to have those shard servers running their own gRPC APIs
//
//        // For now, mock with fake data
//        ShardSearchServiceGrpc.ShardSearchServiceStub stub = StubManager.getBaseStub(ip)
//                .withDeadlineAfter(2, TimeUnit.SECONDS);
//        Random rand = new Random();
//        return List.of(
//                Document.newBuilder()
//                        .setId(rand.nextInt(10000))
//                        .setFaissScore(rand.nextFloat())
//                        .setLuceneScore(rand.nextFloat())
//                        .build()
//        );
//    }
}
