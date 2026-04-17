package com.tradingsaas.tradingcore.domain.port.in;

import static com.tradingsaas.tradingcore.domain.port.in.LoginUseCase.AuthTokens;

public interface RefreshTokenUseCase {

    AuthTokens refresh(String refreshToken);
}
