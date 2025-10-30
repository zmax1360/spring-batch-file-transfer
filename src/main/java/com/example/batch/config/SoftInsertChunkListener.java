package com.example.batch.config;

import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

public class SoftInsertChunkListener implements ChunkListener {

    private final boolean enabled;

    public SoftInsertChunkListener(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        if (enabled) {
            // Mark the current chunk's transaction to rollback-only
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    @Override
    public void afterChunk(ChunkContext context) { }

    @Override
    public void afterChunkError(ChunkContext context) { }
}


