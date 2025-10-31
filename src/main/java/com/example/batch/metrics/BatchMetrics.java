package com.example.batch.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Component;

@Component
public class BatchMetrics {
    private final Counter filesScanned;
    private final Counter filesProcessed;
    private final Counter linesRead;
    private final Counter entityWrites;
    private final Counter payloadWrites;
    private final Counter skipsByReason;
    private final Timer fileProcessingTime;
    private final Timer stepProcessingTime;

    public BatchMetrics(MeterRegistry registry) {
        this.filesScanned = Counter.builder("batch.files.scanned")
                .description("Total files scanned in archive directory")
                .register(registry);
        
        this.filesProcessed = Counter.builder("batch.files.processed")
                .description("Total files successfully processed")
                .register(registry);
        
        this.linesRead = Counter.builder("batch.lines.read")
                .description("Total lines read from files")
                .register(registry);
        
        this.entityWrites = Counter.builder("batch.writes.entity")
                .description("Total entity records written to destination")
                .register(registry);
        
        this.payloadWrites = Counter.builder("batch.writes.payload")
                .description("Total payload records written to destination")
                .register(registry);
        
        this.skipsByReason = Counter.builder("batch.skips")
                .description("Total items skipped by reason")
                .tag("reason", "unknown")
                .register(registry);
        
        this.fileProcessingTime = Timer.builder("batch.file.processing.time")
                .description("Time taken to process each file")
                .register(registry);
        
        this.stepProcessingTime = Timer.builder("batch.step.processing.time")
                .description("Step processing throughput")
                .register(registry);
    }

    public void incrementFilesScanned() {
        filesScanned.increment();
    }

    public void incrementFilesProcessed() {
        filesProcessed.increment();
    }

    public void incrementLinesRead(long count) {
        linesRead.increment(count);
    }

    public void incrementEntityWrites(long count) {
        entityWrites.increment(count);
    }

    public void incrementPayloadWrites(long count) {
        payloadWrites.increment(count);
    }

    public void incrementSkips(String reason) {
        skipsByReason.increment();
    }

    public Timer.Sample startFileProcessing() {
        return Timer.start();
    }

    public void stopFileProcessing(Timer.Sample sample) {
        sample.stop(fileProcessingTime);
    }

    public Timer.Sample startStepProcessing() {
        return Timer.start();
    }

    public void stopStepProcessing(Timer.Sample sample) {
        sample.stop(stepProcessingTime);
    }
}

