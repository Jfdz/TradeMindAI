package com.tradingsaas.tradingcore.application.usecase.portfolio;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioPositionJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class ManagePortfolioPositionUseCaseImpl implements ManagePortfolioPositionUseCase {

    private final PortfolioPositionJpaRepository repository;

    ManagePortfolioPositionUseCaseImpl(PortfolioPositionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void update(UpdateCommand cmd) {
        PortfolioPositionJpaEntity position = findOwned(cmd.positionId(), cmd.userId());
        if ("CLOSED".equals(position.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot edit a closed position");
        }
        position.update(cmd.quantity(), cmd.entryPrice(), cmd.fees(), cmd.notes(), cmd.purchaseDate());
        repository.save(position);
    }

    @Override
    @Transactional
    public void close(CloseCommand cmd) {
        PortfolioPositionJpaEntity position = findOwned(cmd.positionId(), cmd.userId());
        if ("CLOSED".equals(position.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Position is already closed");
        }
        Instant closedAt = cmd.closedAt() != null ? cmd.closedAt() : Instant.now();
        position.close(cmd.exitPrice(), cmd.fees(), closedAt);
        repository.save(position);
    }

    @Override
    @Transactional
    public void delete(UUID positionId, UUID userId) {
        PortfolioPositionJpaEntity position = findOwned(positionId, userId);
        if ("CLOSED".equals(position.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete a closed position — it contributes to realized P&L history");
        }
        repository.delete(position);
    }

    private PortfolioPositionJpaEntity findOwned(UUID positionId, UUID userId) {
        return repository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));
    }
}
