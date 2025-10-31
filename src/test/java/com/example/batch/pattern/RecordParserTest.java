package com.example.batch.pattern;

import com.example.batch.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecordParserTest {

    @Test
    void parsesLineWithCommaDelimiter() {
        AppProperties.Pattern pattern = new AppProperties.Pattern();
        pattern.setDelimiter(",");
        pattern.setFields(List.of(
                createField("datetime", "string"),
                createField("serviceName", "string"),
                createField("id", "string"),
                createField("message", "string")
        ));

        RecordParser parser = new RecordParser(pattern);
        Map<String, Object> result = parser.parse("2024-01-01,entity,1,hello");

        assertThat(result).hasSize(4);
        assertThat(result.get("datetime")).isEqualTo("2024-01-01");
        assertThat(result.get("serviceName")).isEqualTo("entity");
        assertThat(result.get("id")).isEqualTo("1");
        assertThat(result.get("message")).isEqualTo("hello");
    }

    @Test
    void coercesIntegerTypes() {
        AppProperties.Pattern pattern = new AppProperties.Pattern();
        pattern.setDelimiter(",");
        pattern.setFields(List.of(
                createField("id", "int"),
                createField("count", "long")
        ));

        RecordParser parser = new RecordParser(pattern);
        Map<String, Object> result = parser.parse("42,999");

        assertThat(result.get("id")).isEqualTo(42);
        assertThat(result.get("count")).isEqualTo(999L);
    }

    private AppProperties.Field createField(String name, String type) {
        AppProperties.Field f = new AppProperties.Field();
        f.setName(name);
        f.setType(type);
        return f;
    }
}

