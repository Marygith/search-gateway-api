package ru.nms.diplom.searchgateway.util;

import ru.nms.diplom.clusterstate.service.ShardMapping;
import ru.nms.diplom.clusterstate.service.ShardsDistributionResponse;
import ru.nms.diplom.searchgateway.model.ShardGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShardUtils {
    public static List<ShardGroup> groupShardsByIpAndType(ShardsDistributionResponse state) {
        Map<String, ShardGroup> map = new HashMap<>();

        for (ShardMapping mapping : state.getLuceneShardsList()) {
            map.computeIfAbsent(mapping.getHost() + mapping.getPort() + ":0", key -> new ShardGroup(mapping.getHost(), mapping.getPort(), 0))
                    .shardIds.addAll(mapping.getShardsList());
        }
        for (ShardMapping mapping : state.getFaissShardsList()) {
            map.computeIfAbsent(mapping.getHost() + mapping.getPort() + ":1", key -> new ShardGroup(mapping.getHost(), mapping.getPort(), 1))
                    .shardIds.addAll(mapping.getShardsList());
        }

        return new ArrayList<>(map.values());
    }
}
