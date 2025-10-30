package com.example.batch.pattern;

import com.example.batch.config.AppProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LineParser {
    private final String delimiter;
    private final List<AppProperties.Field> fields;

    public LineParser(AppProperties.Pattern pattern) {
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
            switch (type.toLowerCase()) {
                case "int":
                case "integer":
                    return value.isEmpty() ? null : Integer.parseInt(value);
                case "long":
                    return value.isEmpty() ? null : Long.parseLong(value);
                default:
                    return value; // extend for datetime, etc.
            }
        } catch (Exception e) {
            return value; // fallback
        }
    }
}


