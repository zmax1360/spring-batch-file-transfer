package com.example.batch.io;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class GzipLineItemReader implements ItemStreamReader<String>, ItemStream {

    private final Path archiveDir;
    private final String glob;
    private BufferedReader currentReader;
    private Iterator<Path> filesIterator;
    private Path currentFile;
    private final Path moveProcessedTo;
    private final String requiredNameKeyword = "nomatch"; // filter: filenames must contain this keyword
    private long currentLineNumber = 0L;

    // restartability keys
    private static final String CTX_FILE = "gzip.currentFile";
    private static final String CTX_LINE = "gzip.currentLine";
    private static final String CTX_PROCESSED_FILES = "gzip.processedFiles"; // Set of processed file paths
    private String resumeFromFilePath;
    private long resumeFromLine = 0L;
    private Set<String> processedFiles = new HashSet<>();

    public GzipLineItemReader(String archiveDir, String globPattern, String moveProcessedTo) {
        this.archiveDir = Paths.get(archiveDir);
        this.glob = globPattern == null || globPattern.isBlank() ? "*.gz" : globPattern;
        this.moveProcessedTo = (moveProcessedTo == null || moveProcessedTo.isBlank()) ? null : Paths.get(moveProcessedTo);
    }

    @Override
    public String read() throws Exception {
        while (true) {
            if (currentReader == null) {
                if (!advanceToNextFile()) {
                    return null; // no more files
                }
            }
            String line = currentReader.readLine();
            if (line != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                currentLineNumber++;
                return trimmed;
            } else {
                // file completely read; mark as processed in ExecutionContext
                if (currentFile != null) {
                    processedFiles.add(currentFile.toString());
                }
                closeCurrentReader();
                moveCurrentFileIfNeeded();
                currentLineNumber = 0L;
            }
        }
    }

    private boolean advanceToNextFile() throws IOException {
        if (filesIterator == null) {
            final BiPredicate<Path, BasicFileAttributes> matcher = (p, attr) -> {
                if (!attr.isRegularFile()) return false;
                String fileName = p.getFileName().toString().toLowerCase();
                if (!fileName.endsWith(".gz")) return false;
                if (!fileName.contains(requiredNameKeyword)) return false; // only files containing keyword
                // ensure folder pattern .../report/archive/
                Path parent = p.getParent();
                if (parent == null) return false;
                Path parentName = parent.getFileName();
                if (parentName == null || !"archive".equals(parentName.toString())) return false;
                Path grandParent = parent.getParent();
                if (grandParent == null || grandParent.getFileName() == null) return false;
                if (!"report".equals(grandParent.getFileName().toString())) return false;
                // optional higher parent can be instance_* under .../log/report/instance_#/report/archive
                return true;
            };

            Stream<Path> stream = Files.find(archiveDir, Integer.MAX_VALUE, matcher);
            filesIterator = stream.iterator();
        }
        try {
            while (filesIterator.hasNext()) {
                Path next = filesIterator.next();
                // handle resume: fast-forward until the resume file, then open it and skip lines
                if (resumeFromFilePath != null) {
                    if (!next.toString().equals(resumeFromFilePath)) {
                        continue;
                    }
                }
                if (Files.size(next) > 0) {
                    String filePath = next.toString();
                    // skip if already processed (tracked in ExecutionContext)
                    if (processedFiles.contains(filePath)) {
                        continue;
                    }
                    currentFile = next;
                    InputStream fis = Files.newInputStream(next, StandardOpenOption.READ);
                    GZIPInputStream gis = new GZIPInputStream(fis);
                    currentReader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
                    // if resuming inside this file, skip already processed lines
                    if (resumeFromFilePath != null) {
                        skipLines(currentReader, resumeFromLine);
                        currentLineNumber = resumeFromLine;
                        resumeFromFilePath = null;
                        resumeFromLine = 0L;
                    }
                    return true;
                }
            }
            return false;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private static void skipLines(BufferedReader reader, long count) throws IOException {
        for (long i = 0; i < count; i++) {
            if (reader.readLine() == null) {
                break;
            }
        }
    }

    private void closeCurrentReader() {
        if (currentReader != null) {
            try { currentReader.close(); } catch (IOException ignored) {}
            currentReader = null;
        }
    }

    private void moveCurrentFileIfNeeded() throws IOException {
        if (currentFile != null && moveProcessedTo != null) {
            Files.createDirectories(moveProcessedTo);
            Path target = moveProcessedTo.resolve(currentFile.getFileName());
            Files.move(currentFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        currentFile = null;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext != null) {
            if (executionContext.containsKey(CTX_FILE)) {
                this.resumeFromFilePath = executionContext.getString(CTX_FILE);
            }
            if (executionContext.containsKey(CTX_LINE)) {
                this.resumeFromLine = executionContext.getLong(CTX_LINE);
            }
            // restore processed files set from ExecutionContext
            if (executionContext.containsKey(CTX_PROCESSED_FILES)) {
                @SuppressWarnings("unchecked")
                Set<String> saved = (Set<String>) executionContext.get(CTX_PROCESSED_FILES);
                if (saved != null) {
                    processedFiles = new HashSet<>(saved);
                }
            }
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext == null) return;
        if (currentFile != null) {
            executionContext.putString(CTX_FILE, currentFile.toString());
            executionContext.putLong(CTX_LINE, currentLineNumber);
        }
        // persist processed files set to ExecutionContext
        executionContext.put(CTX_PROCESSED_FILES, new HashSet<>(processedFiles));
    }

    @Override
    public void close() throws ItemStreamException {
        closeCurrentReader();
    }
}


