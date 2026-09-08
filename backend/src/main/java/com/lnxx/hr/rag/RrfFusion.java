package com.lnxx.hr.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class RrfFusion {
    private RrfFusion() {}

    public static <T> List<T> fuse(List<List<T>> rankings, Function<T, String> id, int limit) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, T> values = new LinkedHashMap<>();
        for (List<T> ranking : rankings) {
            for (int rank = 0; rank < ranking.size(); rank++) {
                String key = id.apply(ranking.get(rank));
                values.putIfAbsent(key, ranking.get(rank));
                scores.merge(key, 1.0 / (60 + rank + 1), Double::sum);
            }
        }
        return scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit).map(entry -> values.get(entry.getKey())).toList();
    }
}
