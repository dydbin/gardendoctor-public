package com.project.farming.domain.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailDispatcherTest {

    @Mock
    private PasswordResetMailService mailService;

    @Mock
    private PasswordResetTokenStore tokenStore;

    @Test
    void smtpFailureShouldRevokeIssuedToken() {
        PasswordResetMailDispatcher dispatcher = new PasswordResetMailDispatcher(mailService, tokenStore);
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(mailService).sendResetLink("user@example.com", "raw-token");

        dispatcher.dispatch("user@example.com", "raw-token", 7L);

        verify(tokenStore).revoke("raw-token");
    }
}
