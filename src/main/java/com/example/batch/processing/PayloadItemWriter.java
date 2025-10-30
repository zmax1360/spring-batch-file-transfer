package com.example.batch.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PayloadItemWriter implements ItemWriter<RoutedRecord> {
    private static final Logger log = LoggerFactory.getLogger(PayloadItemWriter.class);

    private final JdbcTemplate destinationJdbcTemplate;
    private final String tableName;
    private final String idField;

    public PayloadItemWriter(JdbcTemplate destinationJdbcTemplate, String tableName, String idField) {
        this.destinationJdbcTemplate = destinationJdbcTemplate;
        this.tableName = tableName;
        this.idField = idField;
    }

    @Override
    public void write(Chunk<? extends RoutedRecord> chunk) throws Exception {
        List<Map<String, Object>> toInsert = new ArrayList<>();
        for (RoutedRecord rr : chunk) {
            Map<String, Object> parsed = rr.getParsed();
            if (parsed == null || parsed.isEmpty()) continue;
            Object idValue = parsed.get(idField);
            if (idValue == null) continue;

            // Example: search destination for existing record by ID
            Integer exists = destinationJdbcTemplate.query("SELECT 1 FROM " + tableName + " WHERE " + idField + " = ?",
                    ps -> ps.setObject(1, idValue), rs -> rs.next() ? 1 : 0);

            // Example calculation hook: no-op for now; you can enrich 'parsed' here
            if (exists != null && exists == 1) {
                // Example: skip or perform update if needed (left as insert-or-skip for now)
                log.debug("payload exists in destination, skipping id={}", idValue);
                continue;
            }
            toInsert.add(parsed);
        }

        if (toInsert.isEmpty()) return;

        // Insert parsed fields as-is into destination table (columns must match parsed field names)
        Map<String, Object> first = toInsert.get(0);
        List<String> columns = new ArrayList<>(first.keySet());
        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner qMarks = new StringJoiner(", ");
        for (String col : columns) {
            colJoiner.add(col);
            qMarks.add("?");
        }
        String sql = "INSERT INTO " + tableName + " (" + colJoiner + ") VALUES (" + qMarks + ")";

        destinationJdbcTemplate.batchUpdate(sql, toInsert, 100, (ps, item) -> {
            for (int i = 0; i < columns.size(); i++) {
                ps.setObject(i + 1, item.get(columns.get(i)));
            }
        });
    }
}


