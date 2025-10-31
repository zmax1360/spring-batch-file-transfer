package com.example.batch.config;

import com.example.batch.metrics.BatchMetrics;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class MetricsStepListener implements StepExecutionListener {
    private final BatchMetrics metrics;
    private io.micrometer.core.instrument.Timer.Sample stepSample;

    public MetricsStepListener(BatchMetrics metrics) {
        this.metrics = metrics;
    }

    public MetricsStepListener() {
        this.metrics = null; // will be injected
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        if (metrics != null) {
            stepSample = metrics.startStepProcessing();
        }
    }

    @Override
    public org.springframework.batch.core.ExitStatus afterStep(StepExecution stepExecution) {
        if (metrics != null && stepSample != null) {
            metrics.stopStepProcessing(stepSample);
            long itemsProcessed = stepExecution.getWriteCount();
            if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
                long duration = java.time.Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
                double throughput = itemsProcessed > 0 ? (itemsProcessed * 1000.0) / duration : 0.0;
                stepExecution.getExecutionContext().put("throughput", throughput);
            }
        }
        return stepExecution.getExitStatus();
    }
}

