package com.example.batch.processing;

import com.example.batch.config.AppProperties;
import com.example.batch.pattern.RecordParser;
import com.example.batch.pattern.ServiceNameResolver;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public class DfdrEntityLookupProcessor implements ItemProcessor<String, RoutedRecord> {

    private final JdbcTemplate sourceJdbcTemplate;
    private final String tableName;
    private final String idColumn;
    private final String idFieldName;
    private final String serviceNameField;
    private final RecordParser parser;
    private final ServiceNameResolver serviceNameResolver;

    public DfdrEntityLookupProcessor(JdbcTemplate sourceJdbcTemplate,
                                     String tableName,
                                     String idColumn,
                                     AppProperties.Pattern pattern) {
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.idFieldName = pattern.getIdField();
        this.serviceNameField = pattern.getServiceNameField();
        this.parser = new RecordParser(pattern);
        this.serviceNameResolver = new ServiceNameResolver(pattern);
    }

    @Override
    public RoutedRecord process(String line) {
        Map<String, Object> parsed = parser.parse(line);
        Object idValue = parsed.get(idFieldName);
        if (idValue == null) {
            return null;
        }
        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";
        Map<String, Object> row = sourceJdbcTemplate.query(sql, ps -> ps.setObject(1, idValue), rs -> {
            if (rs.next()) {
                int columnCount = rs.getMetaData().getColumnCount();
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = rs.getMetaData().getColumnLabel(i);
                    if (colName == null || colName.isBlank()) {
                        colName = rs.getMetaData().getColumnName(i);
                    }
                    map.put(colName, rs.getObject(i));
                }
                return map;
            }
            return null;
        });
        String serviceName = serviceNameResolver.resolve(parsed, line, null);
        if ("entity".equalsIgnoreCase(serviceName)) {
            if (row == null) {
                return null;
            }
            return new RoutedRecord(serviceName, row, parsed);
        } else if ("payload".equalsIgnoreCase(serviceName)) {
            // for payload path, pass parsed fields to a specialized writer
            return new RoutedRecord(serviceName, null, parsed);
        } else {
            // default: if row exists, forward it; otherwise drop
            if (row == null) return null;
            return new RoutedRecord(serviceName, row, parsed);
        }
    }
}


