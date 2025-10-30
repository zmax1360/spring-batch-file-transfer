package com.example.batch.processing;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class EntityMapItemWriter implements ItemWriter<Map<String, Object>> {

    private final JdbcTemplate destinationJdbcTemplate;
    private final String tableName;

    public EntityMapItemWriter(JdbcTemplate destinationJdbcTemplate, String tableName) {
        this.destinationJdbcTemplate = destinationJdbcTemplate;
        this.tableName = tableName;
    }

    @Override
    public void write(Chunk<? extends Map<String, Object>> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : chunk) {
            items.add(m);
        }
        Map<String, Object> first = items.get(0);
        List<String> columns = new ArrayList<>(first.keySet());

        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner qMarks = new StringJoiner(", ");
        for (String col : columns) {
            colJoiner.add(col);
            qMarks.add("?");
        }
        String sql = "INSERT INTO " + tableName + " (" + colJoiner + ") VALUES (" + qMarks + ")";

        destinationJdbcTemplate.batchUpdate(sql, items, 100,
                (ps, item) -> {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = item.get(columns.get(i));
                        ps.setObject(i + 1, value);
                    }
                });
    }
}


