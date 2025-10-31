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
        List<Map<String, Object>> items = new ArrayList<>();
        for (RoutedRecord rr : chunk) {
            Map<String, Object> parsed = rr.getParsed();
            if (parsed == null || parsed.isEmpty()) continue;
            Object idValue = parsed.get(idField);
            if (idValue == null) continue;
            
            // Example calculation hook: enrich parsed fields if needed
            // TODO: Add calculation logic here
            
            items.add(parsed);
        }

        if (items.isEmpty()) return;

        // Build MERGE statement for idempotent upsert (no race condition)
        Map<String, Object> first = items.get(0);
        List<String> columns = new ArrayList<>(first.keySet());
        String mergeSql = buildMergeStatement(columns);

        destinationJdbcTemplate.batchUpdate(mergeSql, items, 100, (ps, item) -> {
            int paramIdx = 1;
            // SET values (all columns except ID)
            for (String col : columns) {
                if (!col.equalsIgnoreCase(idField)) {
                    ps.setObject(paramIdx++, item.get(col));
                }
            }
            // VALUES for INSERT
            for (String col : columns) {
                ps.setObject(paramIdx++, item.get(col));
            }
            // ON condition (ID match)
            ps.setObject(paramIdx, item.get(idField));
        });
    }

    private String buildMergeStatement(List<String> columns) {
        StringJoiner setClause = new StringJoiner(", ");
        StringJoiner insertCols = new StringJoiner(", ");
        StringJoiner insertVals = new StringJoiner(", ");
        
        for (String col : columns) {
            insertCols.add(col);
            insertVals.add("?");
            if (!col.equalsIgnoreCase(idField)) {
                setClause.add(col + " = ?");
            }
        }
        
        return String.format(
                "MERGE INTO %s t " +
                "USING (SELECT ? as %s FROM dual) s ON (t.%s = s.%s) " +
                "WHEN MATCHED THEN UPDATE SET %s " +
                "WHEN NOT MATCHED THEN INSERT (%s) VALUES (%s)",
                tableName, idField, idField, idField, setClause, insertCols, insertVals);
    }
}


