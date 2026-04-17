package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.User;

public interface RegisterUserUseCase {

    User register(RegisterCommand cmd);

    record RegisterCommand(String email, String rawPassword, String firstName, String lastName) {}
}
