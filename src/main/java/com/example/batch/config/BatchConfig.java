package com.example.batch.config;

import com.example.batch.io.FileDiscovery;
import com.example.batch.io.GzipSingleFileLineReader;
import com.example.batch.metrics.BatchMetrics;
import com.example.batch.processing.DfdrEntityLookupProcessor;
import com.example.batch.processing.EntityMapItemWriter;
import com.example.batch.processing.RoutedRecord;
import com.example.batch.processing.RoutingItemWriter;
import com.example.batch.processing.PayloadItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    @StepScope
    public MultiResourceItemReader<String> multiResourceReader(AppProperties props,
                                                              @org.springframework.beans.factory.annotation.Value("#{jobParameters['archiveDir']?:'${app.archiveDir}'}") String archiveDir,
                                                              @org.springframework.beans.factory.annotation.Value("#{jobParameters['fileGlob']?:'${app.fileGlob:*.gz}'}") String fileGlob) {
        MultiResourceItemReader<String> reader = new MultiResourceItemReader<>();
        
        try {
            Path baseDir = Paths.get(archiveDir != null ? archiveDir : props.getArchiveDir());
            String glob = fileGlob != null ? fileGlob : (props.getFileGlob() != null ? props.getFileGlob() : "*.gz");
            List<Path> files = FileDiscovery.discoverFiles(baseDir, glob);
            Resource[] resources = files.stream()
                    .map(Path::toAbsolutePath)
                    .map(FileSystemResource::new)
                    .toArray(Resource[]::new);
            reader.setResources(resources);
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover files", e);
        }
        
        ResourceAwareItemReaderItemStream<String> delegate = new GzipSingleFileLineReader();
        reader.setDelegate(delegate);
        reader.setStrict(true);
        return reader;
    }

    @Bean
    public ItemProcessor<String, RoutedRecord> lookupProcessor(JdbcTemplate sourceJdbcTemplate, AppProperties props) {
        return new DfdrEntityLookupProcessor(sourceJdbcTemplate, props.getTableName(), props.getIdColumn(), props.getPattern());
    }

    @Bean
    public ItemWriter<Map<String, Object>> entityWriter(JdbcTemplate destinationJdbcTemplate, AppProperties props) {
        return new EntityMapItemWriter(destinationJdbcTemplate, props.getTableName(), props.getIdColumn());
    }

    @Bean
    public ItemWriter<RoutedRecord> routingWriter(ItemWriter<Map<String, Object>> entityWriter,
                                                  JdbcTemplate destinationJdbcTemplate,
                                                  AppProperties props) {
        java.util.Map<String, ItemWriter<java.util.Map<String, Object>>> routes = new java.util.HashMap<>();
        // route 'entity' to DB row writer (uses payload from source DB)
        routes.put("entity", entityWriter);
        // route 'payload' to a writer that consults destination and performs calculations/inserts parsed fields
        ItemWriter<RoutedRecord> payloadWriter = new PayloadItemWriter(destinationJdbcTemplate, props.getTableName(), props.getPattern().getIdField());
        return new RoutingItemWriter(routes, entityWriter) {
            @Override
            public void write(org.springframework.batch.item.Chunk<? extends RoutedRecord> chunk) throws Exception {
                java.util.List<RoutedRecord> payloads = new java.util.ArrayList<>();
                org.springframework.batch.item.Chunk<RoutedRecord> others = new org.springframework.batch.item.Chunk<>();
                for (RoutedRecord rr : chunk) {
                    if ("payload".equalsIgnoreCase(rr.getServiceName())) {
                        payloads.add(rr);
                    } else {
                        others.add(rr);
                    }
                }
                if (!others.isEmpty()) {
                    super.write(others);
                }
                if (!payloads.isEmpty()) {
                    org.springframework.batch.item.Chunk<RoutedRecord> payloadChunk = new org.springframework.batch.item.Chunk<>();
                    payloadChunk.addAll(payloads);
                    payloadWriter.write(payloadChunk);
                }
            }
        };
    }

    @Bean
    public Step transferStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             MultiResourceItemReader<String> reader,
                             ItemProcessor<String, RoutedRecord> processor,
                             ItemWriter<RoutedRecord> writer,
                             AppProperties props,
                             BatchMetrics metrics) {
        return new StepBuilder("transferStep", jobRepository)
                .<String, RoutedRecord>chunk(1000, transactionManager) // tuned chunk size
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(java.sql.SQLException.class)
                .retry(java.net.SocketTimeoutException.class)
                .skip(Exception.class)
                .skipLimit(100) // cap skips
                .listener(new SoftInsertChunkListener(props.isSoftInsert()))
                .listener(new MetricsStepListener(metrics))
                .build();
    }

    @Bean
    public Job transferJob(JobRepository jobRepository, Step transferStep) {
        return new JobBuilder("transferJob", jobRepository)
                .start(transferStep)
                .build();
    }
}


