package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SubscriptionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserEntityMapper {

    @Mapping(target = "subscription", source = "subscription")
    User toDomain(UserJpaEntity entity);

    @Mapping(target = "userId", source = "user.id")
    Subscription subscriptionToDomain(SubscriptionJpaEntity entity);
}
