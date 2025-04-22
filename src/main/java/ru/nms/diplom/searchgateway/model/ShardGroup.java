package ru.nms.diplom.searchgateway.model;

import java.util.ArrayList;
import java.util.List;

public class ShardGroup {
    public String ip;
    public int indexType; // 0 = FAISS, 1 = Lucene
    public List<Integer> shardIds = new ArrayList<>();

    public ShardGroup(String ip, int indexType) {
        this.ip = ip;
        this.indexType = indexType;
    }
}