package com.example.batch.pattern;

import com.example.batch.config.AppProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecordParser {
    private final String delimiter;
    private final List<AppProperties.Field> fields;

    public RecordParser(AppProperties.Pattern pattern) {
        this.delimiter = pattern.getDelimiter();
        this.fields = pattern.getFields();
    }

    public Map<String, Object> parse(String line) {
        String[] tokens = line.split(java.util.regex.Pattern.quote(delimiter), -1);
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String value = i < tokens.length ? tokens[i] : "";
            AppProperties.Field f = fields.get(i);
            result.put(f.getName(), coerce(value, f.getType()));
        }
        return result;
    }

    private Object coerce(String value, String type) {
        if (type == null || type.isBlank() || "string".equalsIgnoreCase(type)) {
            return value;
        }
        try {
            return switch (type.toLowerCase()) {
                case "int", "integer" -> value.isEmpty() ? null : Integer.parseInt(value);
                case "long" -> value.isEmpty() ? null : Long.parseLong(value);
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
    }
}

