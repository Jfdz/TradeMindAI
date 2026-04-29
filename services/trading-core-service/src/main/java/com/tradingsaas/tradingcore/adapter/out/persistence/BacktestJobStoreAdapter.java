package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.BacktestJobJpaEntity;
import com.tradingsaas.tradingcore.application.usecase.backtest.BacktestJobStore;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class BacktestJobStoreAdapter implements BacktestJobStore {

    private final BacktestJobJpaRepository repository;
    private final ObjectMapper objectMapper;

    BacktestJobStoreAdapter(BacktestJobJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BacktestJob save(BacktestJob job) {
        return toDomain(repository.save(toEntity(job)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BacktestJob> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BacktestJob> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public BacktestJob updateStatus(UUID id, BacktestStatus status, Instant updatedAt) {
        return repository.findById(id)
                .map(current -> new BacktestJobJpaEntity(
                        current.getId(),
                        current.getSymbol(),
                        current.getFromDate(),
                        current.getToDate(),
                        current.getQuantity(),
                        status,
                        current.getResultPayload(),
                        current.getErrorMessage(),
                        current.getCreatedAt(),
                        updatedAt
                ))
                .map(repository::save)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional
    public BacktestJob complete(UUID id, BacktestResult result, Instant updatedAt) {
        return repository.findById(id)
                .map(current -> new BacktestJobJpaEntity(
                        current.getId(),
                        current.getSymbol(),
                        current.getFromDate(),
                        current.getToDate(),
                        current.getQuantity(),
                        BacktestStatus.COMPLETED,
                        serialize(result),
                        null,
                        current.getCreatedAt(),
                        updatedAt
                ))
                .map(repository::save)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional
    public BacktestJob fail(UUID id, String errorMessage, Instant updatedAt) {
        return repository.findById(id)
                .map(current -> new BacktestJobJpaEntity(
                        current.getId(),
                        current.getSymbol(),
                        current.getFromDate(),
                        current.getToDate(),
                        current.getQuantity(),
                        BacktestStatus.FAILED,
                        current.getResultPayload(),
                        errorMessage,
                        current.getCreatedAt(),
                        updatedAt
                ))
                .map(repository::save)
                .map(this::toDomain)
                .orElse(null);
    }

    private BacktestJobJpaEntity toEntity(BacktestJob job) {
        return new BacktestJobJpaEntity(
                job.id(),
                job.request().symbol(),
                job.request().from(),
                job.request().to(),
                job.request().quantity(),
                job.status(),
                serialize(job.result()),
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private BacktestJob toDomain(BacktestJobJpaEntity entity) {
        return new BacktestJob(
                entity.getId(),
                new BacktestRequest(
                        entity.getSymbol(),
                        entity.getFromDate(),
                        entity.getToDate(),
                        entity.getQuantity()
                ),
                entity.getStatus(),
                deserialize(entity.getResultPayload()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getErrorMessage()
        );
    }

    private String serialize(BacktestResult result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize backtest result", ex);
        }
    }

    private BacktestResult deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, BacktestResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize backtest result", ex);
        }
    }
}
