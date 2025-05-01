package ru.nms.diplom.searchgateway.model;

import java.util.ArrayList;
import java.util.List;

public class ShardGroup {
    public String host;
    public int port;
    public int indexType; // 0 = FAISS, 1 = Lucene
    public List<Integer> shardIds = new ArrayList<>();

    public ShardGroup(String host, int port, int indexType) {
        this.host = host;
        this.port = port;
        this.indexType = indexType;
    }
}