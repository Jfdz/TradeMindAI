package com.tradingsaas.tradingcore.domain.port.in;

public interface LogoutUseCase {

    void logout(String refreshToken, String accessToken);
}
