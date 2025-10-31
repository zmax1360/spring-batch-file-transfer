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
    private final String idColumn;

    public EntityMapItemWriter(JdbcTemplate destinationJdbcTemplate, String tableName, String idColumn) {
        this.destinationJdbcTemplate = destinationJdbcTemplate;
        this.tableName = tableName;
        this.idColumn = idColumn;
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

        // Build MERGE statement for idempotent upsert
        String mergeSql = buildMergeStatement(columns);

        destinationJdbcTemplate.batchUpdate(mergeSql, items, 100,
                (ps, item) -> {
                    int paramIdx = 1;
                    // SET values (all columns except ID)
                    for (String col : columns) {
                        if (!col.equalsIgnoreCase(idColumn)) {
                            ps.setObject(paramIdx++, item.get(col));
                        }
                    }
                    // VALUES for INSERT
                    for (String col : columns) {
                        ps.setObject(paramIdx++, item.get(col));
                    }
                    // ON condition (ID match)
                    ps.setObject(paramIdx, item.get(idColumn));
                });
    }

    private String buildMergeStatement(List<String> columns) {
        StringJoiner setClause = new StringJoiner(", ");
        StringJoiner insertCols = new StringJoiner(", ");
        StringJoiner insertVals = new StringJoiner(", ");
        
        for (String col : columns) {
            insertCols.add(col);
            insertVals.add("?");
            if (!col.equalsIgnoreCase(idColumn)) {
                setClause.add(col + " = ?");
            }
        }
        
        return String.format(
                "MERGE INTO %s t " +
                "USING (SELECT ? as %s FROM dual) s ON (t.%s = s.%s) " +
                "WHEN MATCHED THEN UPDATE SET %s " +
                "WHEN NOT MATCHED THEN INSERT (%s) VALUES (%s)",
                tableName, idColumn, idColumn, idColumn, setClause, insertCols, insertVals);
    }
}


