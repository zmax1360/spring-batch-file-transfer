package com.example.batch.pattern;

import com.example.batch.config.AppProperties;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceNameResolver {
    public enum Mode { FIELD, LINE_REGEX, FILE_NAME_REGEX }

    private final Mode mode;
    private final String fieldName; // when FIELD
    private final Pattern regex; // when LINE_REGEX or FILE_NAME_REGEX

    public ServiceNameResolver(AppProperties.Pattern pattern) {
        String configuredMode = pattern.getServiceNameMode();
        this.mode = configuredMode == null ? Mode.FIELD : Mode.valueOf(configuredMode.toUpperCase());
        this.fieldName = pattern.getServiceNameField();
        String rx = pattern.getServiceNameRegex();
        this.regex = (rx == null || rx.isBlank()) ? null : Pattern.compile(rx);
    }

    public String resolve(Map<String, Object> parsedLine, String rawLine, Path filePath) {
        switch (mode) {
            case FIELD:
                Object v = parsedLine.get(fieldName);
                return v == null ? "" : String.valueOf(v);
            case LINE_REGEX:
                if (regex == null || rawLine == null) return "";
                Matcher m = regex.matcher(rawLine);
                return m.find() && m.groupCount() >= 1 ? m.group(1) : "";
            case FILE_NAME_REGEX:
                if (regex == null || filePath == null) return "";
                String name = filePath.getFileName().toString();
                Matcher m2 = regex.matcher(name);
                return m2.find() && m2.groupCount() >= 1 ? m2.group(1) : "";
            default:
                return "";
        }
    }
}


