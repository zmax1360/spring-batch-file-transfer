package com.example.batch.processing;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoutingItemWriter implements ItemWriter<RoutedRecord> {

    private final Map<String, ItemWriter<Map<String, Object>>> routes;
    private final ItemWriter<Map<String, Object>> defaultWriter;

    public RoutingItemWriter(Map<String, ItemWriter<Map<String, Object>>> routes,
                             ItemWriter<Map<String, Object>> defaultWriter) {
        this.routes = new HashMap<>(routes);
        this.defaultWriter = defaultWriter;
    }

    @Override
    public void write(Chunk<? extends RoutedRecord> chunk) throws Exception {
        Map<ItemWriter<Map<String, Object>>, List<Map<String, Object>>> batches = new HashMap<>();
        for (RoutedRecord rr : chunk) {
            ItemWriter<Map<String, Object>> w = routes.getOrDefault(rr.getServiceName(), defaultWriter);
            batches.computeIfAbsent(w, k -> new ArrayList<>()).add(rr.getPayload());
        }
        for (Map.Entry<ItemWriter<Map<String, Object>>, List<Map<String, Object>>> e : batches.entrySet()) {
            ItemWriter<Map<String, Object>> w = e.getKey();
            List<Map<String, Object>> items = e.getValue();
            w.write(Chunk.of(items));
        }
    }
}


