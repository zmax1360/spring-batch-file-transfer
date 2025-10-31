package com.example.batch.io;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class GzipSingleFileLineReader implements ResourceAwareItemReaderItemStream<String> {
    private static final String CTX_CURRENT_LINE = "gzip.currentLine";
    
    private Resource resource;
    private BufferedReader reader;
    private long currentLineNumber = 0L;
    private long resumeFromLine = 0L;

    @Override
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    @Override
    public String read() throws Exception {
        if (reader == null) {
            if (resource == null || !resource.exists()) {
                return null;
            }
            openReader();
        }
        
        String line = reader.readLine();
        if (line != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                currentLineNumber++;
                return trimmed;
            }
            return read(); // skip empty lines
        }
        return null; // EOF
    }

    private void openReader() throws IOException {
        InputStream fis = resource.getInputStream();
        GZIPInputStream gis = new GZIPInputStream(fis);
        reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
        
        // skip to resume line if needed
        for (long i = 0; i < resumeFromLine; i++) {
            if (reader.readLine() == null) break;
        }
        currentLineNumber = resumeFromLine;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext != null && executionContext.containsKey(CTX_CURRENT_LINE)) {
            this.resumeFromLine = executionContext.getLong(CTX_CURRENT_LINE);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext != null && reader != null) {
            executionContext.putLong(CTX_CURRENT_LINE, currentLineNumber);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
            reader = null;
        }
    }
}

