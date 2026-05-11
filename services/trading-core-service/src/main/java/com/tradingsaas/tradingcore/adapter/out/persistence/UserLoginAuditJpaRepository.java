package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserLoginAuditJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserLoginAuditJpaRepository extends JpaRepository<UserLoginAuditJpaEntity, UUID> {

    @Query("SELECT e FROM UserLoginAuditJpaEntity e WHERE e.userId = :userId ORDER BY e.loggedInAt DESC LIMIT :limit")
    List<UserLoginAuditJpaEntity> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}
