package com.famelsvg.reservasalas.user;

import com.famelsvg.reservasalas.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Cria o admin inicial só no perfil dev, a partir de ADMIN_EMAIL/ADMIN_PASSWORD. */
@Component
@Profile("dev")
public class DevAdminBootstrap implements ApplicationRunner {

    private final Environment env;
    private final AuthService authService;
    private final boolean enabled;

    public DevAdminBootstrap(Environment env, AuthService authService,
                             @Value("${app.dev.admin-enabled:false}") boolean enabled) {
        this.env = env;
        this.authService = authService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = env.getProperty("ADMIN_EMAIL");
        String password = env.getProperty("ADMIN_PASSWORD");
        if (!enabled || isBlank(email) || isBlank(password)) {
            return;
        }
        authService.createUserIfAbsent(email, password, Role.ADMIN);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
