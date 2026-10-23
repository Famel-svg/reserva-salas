package com.famelsvg.reservasalas.user;

import com.famelsvg.reservasalas.common.error.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw new ConflictException("E-mail já cadastrado");
        }
        User saved = users.save(new User(email, encoder.encode(request.password()), request.role()));
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole());
    }
}
