package com.example.batch.processing;

import java.util.Map;

public class RoutedRecord {
    private final String serviceName;
    private final Map<String, Object> payload; // DB row when service=entity; may be null for others
    private final Map<String, Object> parsed;  // parsed fields from the line

    public RoutedRecord(String serviceName, Map<String, Object> payload, Map<String, Object> parsed) {
        this.serviceName = serviceName;
        this.payload = payload;
        this.parsed = parsed;
    }

    public String getServiceName() { return serviceName; }
    public Map<String, Object> getPayload() { return payload; }
    public Map<String, Object> getParsed() { return parsed; }
}


