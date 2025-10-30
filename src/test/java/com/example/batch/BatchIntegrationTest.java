package com.example.batch;

import com.example.batch.config.AppProperties;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BatchIntegrationTest {

    @Autowired
    JdbcTemplate sourceJdbcTemplate;
    @Autowired
    JdbcTemplate destinationJdbcTemplate;
    @Autowired
    AppProperties props;
    @Autowired
    JobLauncher jobLauncher;
    @Autowired
    Job transferJob;

    @BeforeEach
    void setupDbsAndFiles() throws Exception {
        // DDL for both DBs
        sourceJdbcTemplate.execute("DROP TABLE IF EXISTS dfdr_entity");
        destinationJdbcTemplate.execute("DROP TABLE IF EXISTS dfdr_entity");
        String ddl = "CREATE TABLE dfdr_entity (" +
                "id VARCHAR(64) PRIMARY KEY, " +
                "datetime VARCHAR(64), " +
                "serviceName VARCHAR(128), " +
                "message VARCHAR(1024)" +
                ")";
        sourceJdbcTemplate.execute(ddl);
        destinationJdbcTemplate.execute(ddl);

        // seed source with id=1 row to be copied for service=entity
        sourceJdbcTemplate.update("INSERT INTO dfdr_entity (id, datetime, serviceName, message) VALUES (?,?,?,?)",
                "1", "2024-01-01T00:00:00Z", "entity", "source-row");

        // prepare test archive path
        Path base = Path.of("target/test-archive");
        Path report = base.resolve("log/report/instance_1/report/archive");
        Files.createDirectories(report);

        // write a .gz file containing two lines: one entity, one payload; include 'nomatch' in file name per filter
        Path gz = report.resolve("events-nomatch-001.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gz.toFile()));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gos, StandardCharsets.UTF_8))) {
            bw.write("2024-01-01,entity,1,hello-entity\n");
            bw.write("2024-01-02,payload,2,hello-payload\n");
        }
    }

    @Test
    void endToEnd_entityAndPayload() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(transferJob, params);

        List<Map<String, Object>> rows = destinationJdbcTemplate.queryForList("SELECT * FROM dfdr_entity ORDER BY id");
        assertThat(rows).hasSize(2);

        Map<String, Object> r1 = rows.get(0);
        assertThat(r1.get("ID").toString()).isEqualTo("1");
        assertThat(r1.get("SERVICENAME").toString().toLowerCase()).isEqualTo("entity");

        Map<String, Object> r2 = rows.get(1);
        assertThat(r2.get("ID").toString()).isEqualTo("2");
        assertThat(r2.get("SERVICENAME").toString().toLowerCase()).isEqualTo("payload");
        assertThat(r2.get("MESSAGE").toString()).contains("hello-payload");
    }
}


