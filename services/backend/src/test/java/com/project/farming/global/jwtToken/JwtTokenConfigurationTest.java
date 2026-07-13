package com.project.farming.global.jwtToken;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenConfigurationTest {

    private static final String ACCESS_SECRET = "local-test-access-secret-key-local-test-access-123456";
    private static final String REFRESH_SECRET = "local-test-refresh-secret-key-local-test-refresh-123456";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JwtTokenProvider.class)
            .withPropertyValues(
                    "jwt.expiration=3600000",
                    "jwt.refresh-expiration=1209600000"
            );

    @Test
    void missingAccessSecretPreventsContextStartup() {
        contextRunner
                .withPropertyValues("jwt.refresh-secret=" + REFRESH_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("jwt.access-secret must be configured");
                });
    }

    @Test
    void missingRefreshSecretPreventsContextStartup() {
        contextRunner
                .withPropertyValues("jwt.access-secret=" + ACCESS_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("jwt.refresh-secret must be configured");
                });
    }

    @Test
    void distinctStrongSecretsStartContext() {
        contextRunner
                .withPropertyValues(
                        "jwt.access-secret=" + ACCESS_SECRET,
                        "jwt.refresh-secret=" + REFRESH_SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
