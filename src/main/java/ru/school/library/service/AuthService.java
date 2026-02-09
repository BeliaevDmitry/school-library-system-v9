package ru.school.library.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.school.library.entity.User;
import ru.school.library.repo.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;

    public User requireUser(String username) {
        return users.findByUsername(username).orElseThrow();
    }
}
