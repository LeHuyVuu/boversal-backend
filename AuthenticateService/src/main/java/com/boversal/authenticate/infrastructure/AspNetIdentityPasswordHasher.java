package com.boversal.authenticate.infrastructure;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

import com.boversal.authenticate.application.PasswordHasher;

@Component
public class AspNetIdentityPasswordHasher implements PasswordHasher {
    private static final int FORMAT_MARKER = 0x01;
    private static final int PRF_HMAC_SHA512 = 0x00000002;
    private static final int ITERATIONS = 100_000;
    private static final int SALT_SIZE = 16;
    private static final int SUBKEY_SIZE = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String hash(String rawPassword) {
        var salt = new byte[SALT_SIZE];
        random.nextBytes(salt);
        var subkey = derive(rawPassword, salt, ITERATIONS, SUBKEY_SIZE);
        var payload = ByteBuffer.allocate(17 + salt.length + subkey.length)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) FORMAT_MARKER)
                .putInt(PRF_HMAC_SHA512)
                .putInt(ITERATIONS)
                .putInt(SALT_SIZE)
                .putInt(SUBKEY_SIZE)
                .put(salt)
                .put(subkey)
                .array();
        return Base64.getEncoder().encodeToString(payload);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        try {
            var payload = Base64.getDecoder().decode(encodedHash);
            if (payload.length < 17 || payload[0] != FORMAT_MARKER) {
                return false;
            }

            var buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            buffer.get();
            var prf = buffer.getInt();
            var iterations = buffer.getInt();
            var saltLength = buffer.getInt();
            var subkeyLength = buffer.getInt();
            if (prf != PRF_HMAC_SHA512 || saltLength <= 0 || subkeyLength <= 0
                    || payload.length != 17 + saltLength + subkeyLength) {
                return false;
            }

            var salt = new byte[saltLength];
            var expectedSubkey = new byte[subkeyLength];
            buffer.get(salt).get(expectedSubkey);
            var actualSubkey = derive(rawPassword, salt, iterations, subkeyLength);
            return MessageDigest.isEqual(expectedSubkey, actualSubkey);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations, int subkeySize) {
        try {
            var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, subkeySize * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive password hash", exception);
        }
    }
}