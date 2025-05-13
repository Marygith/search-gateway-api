package ru.nms.diplom.searchgateway.service;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntDoubleImmutablePair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ru.nms.diplom.shardsearch.Document;

public class ScoringUtils {
    public static List<Integer> getFinalResult(Collection<ru.nms.diplom.shardsearch.Document.Builder> documents, int originalK) {
        var docIds = new IntOpenHashSet(documents.size());
        List<ru.nms.diplom.shardsearch.Document> deduplicatedDocs = new ArrayList<>(documents.size());
        float maxLuceneScore = Float.MIN_VALUE;
        float minLuceneScore = Float.MAX_VALUE;
        float maxFaissDistance = Float.MIN_VALUE;
        float minFaissDistance = Float.MAX_VALUE;
        for (var doc : documents) {
            if (docIds.add(doc.getId())) {
                deduplicatedDocs.add(doc.build());
                maxFaissDistance = Math.max(maxFaissDistance, doc.getFaissScore());
                minFaissDistance = Math.min(minFaissDistance, doc.getFaissScore());
                maxLuceneScore = Math.max(maxLuceneScore, doc.getLuceneScore());
                minLuceneScore = Math.min(minLuceneScore, doc.getLuceneScore());
            }
        }

        if (minLuceneScore < 0) {
            System.out.println("negative lucene score: " + minLuceneScore);
        }
        float finalMinFaissDistance = minFaissDistance;
        float finalMaxFaissDistance = maxFaissDistance;
        float finalMinLuceneScore = minLuceneScore;
        float finalMaxLuceneScore = maxLuceneScore;
        return deduplicatedDocs.stream()
            .map(d -> new IntDoubleImmutablePair(d.getId(),
                calculateAggregatedScore(d, finalMaxLuceneScore, finalMinLuceneScore, finalMinFaissDistance, finalMaxFaissDistance)))
            .sorted(Comparator.comparingDouble(Pair::right))
            .limit(originalK)
            .map(Pair::left)
            .toList();
    }

    private static double calculateAggregatedScore(
        ru.nms.diplom.shardsearch.Document doc,
        float maxLuceneScore,
        float minLuceneScore,
        float maxFaissDistance,
        float minFaissDistance
    ) {
        return
            (5.4246 * (1 - (doc.getFaissScore() - minFaissDistance) / (maxFaissDistance - minFaissDistance))
                + 0.7427 * ((doc.getLuceneScore() - minLuceneScore) / (maxLuceneScore - minLuceneScore)));
    }
}
