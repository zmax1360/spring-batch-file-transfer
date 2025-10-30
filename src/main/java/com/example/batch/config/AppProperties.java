package com.example.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String archiveDir;
    private String moveProcessedTo;
    private String fileGlob;
    private String tableName;
    private String idColumn;
    private Pattern pattern;

    public String getArchiveDir() {
        return archiveDir;
    }

    public void setArchiveDir(String archiveDir) {
        this.archiveDir = archiveDir;
    }

    public String getMoveProcessedTo() {
        return moveProcessedTo;
    }

    public void setMoveProcessedTo(String moveProcessedTo) {
        this.moveProcessedTo = moveProcessedTo;
    }

    public String getFileGlob() {
        return fileGlob;
    }

    public void setFileGlob(String fileGlob) {
        this.fileGlob = fileGlob;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public static class Pattern {
        private String delimiter = ",";
        private String serviceNameField;
        private String serviceNameMode; // FIELD, LINE_REGEX, FILE_NAME_REGEX
        private String serviceNameRegex; // used when regex modes
        private String idField;
        private java.util.List<Field> fields;

        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

        public String getServiceNameField() { return serviceNameField; }
        public void setServiceNameField(String serviceNameField) { this.serviceNameField = serviceNameField; }

        public String getServiceNameMode() { return serviceNameMode; }
        public void setServiceNameMode(String serviceNameMode) { this.serviceNameMode = serviceNameMode; }

        public String getServiceNameRegex() { return serviceNameRegex; }
        public void setServiceNameRegex(String serviceNameRegex) { this.serviceNameRegex = serviceNameRegex; }

        public String getIdField() { return idField; }
        public void setIdField(String idField) { this.idField = idField; }

        public java.util.List<Field> getFields() { return fields; }
        public void setFields(java.util.List<Field> fields) { this.fields = fields; }
    }

    public static class Field {
        private String name;
        private String type; // string, int, long, datetime (extend as needed)

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}


