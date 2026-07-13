package com.project.farming.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class CredentialFingerprint {

    private CredentialFingerprint() {
    }

    static String email(String email) {
        String normalized = email == null || email.trim().isEmpty()
                ? "__blank__"
                : email.trim().toLowerCase(Locale.ROOT);
        return sha256(normalized);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }
}
