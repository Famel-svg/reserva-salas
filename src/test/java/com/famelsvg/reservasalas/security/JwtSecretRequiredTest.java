package com.famelsvg.reservasalas.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Carrega o application.yml real (sem valor padrao para JWT_SECRET) e so o JwtKeyConfig. Nao precisa de Docker. */
class JwtSecretRequiredTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(JwtKeyConfig.class);

    @Test
    void contextFailsWhenSecretIsEmpty() {
        runner.withPropertyValues("app.jwt.secret=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("JWT_SECRET");
        });
    }

    @Test
    void contextFailsWithShortSecret() {
        runner.withPropertyValues("app.jwt.secret=curto").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("32 bytes");
        });
    }

    @Test
    void contextStartsWithValidSecret() {
        runner.withPropertyValues("app.jwt.secret=0123456789abcdef0123456789abcdef")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }
}
