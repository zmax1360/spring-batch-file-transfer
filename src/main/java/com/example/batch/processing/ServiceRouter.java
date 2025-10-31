package com.example.batch.processing;

import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceRouter {
    private final Map<String, ItemWriter<RoutedRecord>> routes = new HashMap<>();
    private final ItemWriter<RoutedRecord> defaultWriter;

    public ServiceRouter(ItemWriter<Map<String, Object>> entityWriter,
                         ItemWriter<RoutedRecord> payloadWriter) {
        routes.put("entity", new RoutingItemWriterAdapter(entityWriter));
        routes.put("payload", payloadWriter);
        this.defaultWriter = routes.get("entity");
    }

    public ItemWriter<RoutedRecord> route(String serviceName) {
        return routes.getOrDefault(serviceName, defaultWriter);
    }

    // Adapter to convert RoutedRecord to Map<String, Object> for entity writer
    private static class RoutingItemWriterAdapter implements ItemWriter<RoutedRecord> {
        private final ItemWriter<Map<String, Object>> delegate;
        
        RoutingItemWriterAdapter(ItemWriter<Map<String, Object>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(org.springframework.batch.item.Chunk<? extends RoutedRecord> chunk) throws Exception {
            org.springframework.batch.item.Chunk<Map<String, Object>> maps = new org.springframework.batch.item.Chunk<>();
            for (RoutedRecord rr : chunk) {
                if (rr.getPayload() != null) {
                    maps.add(rr.getPayload());
                }
            }
            if (!maps.isEmpty()) {
                delegate.write(maps);
            }
        }
    }
}

