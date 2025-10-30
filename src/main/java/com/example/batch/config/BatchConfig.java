package com.example.batch.config;

import com.example.batch.io.GzipLineItemReader;
import com.example.batch.processing.DfdrEntityLookupProcessor;
import com.example.batch.processing.EntityMapItemWriter;
import com.example.batch.processing.RoutedRecord;
import com.example.batch.processing.RoutingItemWriter;
import com.example.batch.processing.PayloadItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    public ItemReader<String> gzipLineReader(AppProperties props) {
        return new GzipLineItemReader(props.getArchiveDir(), props.getFileGlob(), props.getMoveProcessedTo());
    }

    @Bean
    public ItemProcessor<String, RoutedRecord> lookupProcessor(JdbcTemplate sourceJdbcTemplate, AppProperties props) {
        return new DfdrEntityLookupProcessor(sourceJdbcTemplate, props.getTableName(), props.getIdColumn(), props.getPattern());
    }

    @Bean
    public ItemWriter<Map<String, Object>> entityWriter(JdbcTemplate destinationJdbcTemplate, AppProperties props) {
        return new EntityMapItemWriter(destinationJdbcTemplate, props.getTableName());
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
                org.springframework.batch.item.Chunk<RoutedRecord> others = org.springframework.batch.item.Chunk.of();
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
                    payloadWriter.write(org.springframework.batch.item.Chunk.of(payloads));
                }
            }
        };
    }

    @Bean
    public Step transferStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             ItemReader<String> reader,
                             ItemProcessor<String, RoutedRecord> processor,
                             ItemWriter<RoutedRecord> writer,
                             AppProperties props) {
        return new StepBuilder("transferStep", jobRepository)
                .<String, RoutedRecord>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(new SoftInsertChunkListener(props.isSoftInsert()))
                .build();
    }

    @Bean
    public Job transferJob(JobRepository jobRepository, Step transferStep) {
        return new JobBuilder("transferJob", jobRepository)
                .start(transferStep)
                .build();
    }
}


