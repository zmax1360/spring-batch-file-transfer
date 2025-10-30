package com.example.batch;

import com.example.batch.io.GzipLineItemReader;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GzipLineItemReaderTest {

    @Test
    void readsOnlyNomatchGzUnderReportArchive() throws Exception {
        Path base = Files.createTempDirectory("reader-test");
        Path archive = base.resolve("x/log/report/instance_99/report/archive");
        Files.createDirectories(archive);

        // file to include
        Path gz1 = archive.resolve("a-nomatch-1.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gz1.toFile()));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gos, StandardCharsets.UTF_8))) {
            bw.write("L1\n");
            bw.write("\n");
            bw.write("L2\n");
        }
        // file to exclude (no nomatch)
        Path gz2 = archive.resolve("a-other-1.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gz2.toFile()));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gos, StandardCharsets.UTF_8))) {
            bw.write("X\n");
        }

        GzipLineItemReader reader = new GzipLineItemReader(base.toString(), "*.gz", null);
        reader.open(new org.springframework.batch.item.ExecutionContext());
        List<String> lines = new ArrayList<>();
        for (;;) {
            String s = reader.read();
            if (s == null) break;
            lines.add(s);
        }
        reader.close();

        assertThat(lines).containsExactly("L1", "L2");
    }
}


