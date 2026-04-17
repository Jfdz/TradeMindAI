package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.EmailAlreadyExistsException;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.RegisterUserUseCase;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    RegisterUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(RegisterCommand cmd) {
        if (userRepository.existsByEmail(cmd.email())) {
            throw new EmailAlreadyExistsException(cmd.email());
        }

        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Subscription subscription = new Subscription(subscriptionId, userId, SubscriptionPlan.FREE, now, null);

        User user = new User(
                userId,
                cmd.email(),
                passwordEncoder.encode(cmd.rawPassword()),
                cmd.firstName(),
                cmd.lastName(),
                subscription,
                now,
                true
        );

        return userRepository.save(user);
    }
}
