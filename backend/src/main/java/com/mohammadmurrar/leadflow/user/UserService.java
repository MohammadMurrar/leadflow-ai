package com.mohammadmurrar.leadflow.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) { this.repository = repository; }

    @Transactional
    public void recordSuccessfulLogin(UUID id) {
        repository.findById(id).ifPresent(User::recordSuccessfulLogin);
    }
}
