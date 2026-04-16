package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface SymbolJpaRepository extends JpaRepository<SymbolEntity, String> {
}
