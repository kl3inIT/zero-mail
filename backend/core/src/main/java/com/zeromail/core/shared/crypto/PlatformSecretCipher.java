package com.zeromail.core.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM-256 envelope cipher for platform-scoped secrets with caller-supplied AAD. */
public final class PlatformSecretCipher {

    private final Map<Integer, SecretKey> keysByVersion;
    private final int currentVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public PlatformSecretCipher(Map<Integer, SecretKey> keysByVersion, int currentVersion) {
        this.keysByVersion = Map.copyOf(keysByVersion);
        this.currentVersion = currentVersion;
    }

    public byte[] encrypt(byte[] plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[12];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keysByVersion.get(currentVersion),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer envelopeBuffer = ByteBuffer.allocate(4 + 12 + ciphertext.length);
            envelopeBuffer.putInt(currentVersion);
            envelopeBuffer.put(nonce);
            envelopeBuffer.put(ciphertext);
            return envelopeBuffer.array();
        } catch (GeneralSecurityException securityException) {
            throw new IllegalStateException(securityException);
        }
    }

    public byte[] decrypt(byte[] envelope, String associatedData) {
        try {
            ByteBuffer envelopeBuffer = ByteBuffer.wrap(envelope);
            int keyVersion = envelopeBuffer.getInt();
            byte[] nonce = new byte[12];
            envelopeBuffer.get(nonce);
            byte[] ciphertext = new byte[envelopeBuffer.remaining()];
            envelopeBuffer.get(ciphertext);
            SecretKey key = keysByVersion.get(keyVersion);
            if (key == null) {
                throw new IllegalStateException("unknown key version " + keyVersion);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException securityException) {
            throw new IllegalStateException(securityException);
        }
    }

    public int currentVersion() {
        return currentVersion;
    }

    public static short keyVersionFromEnvelope(byte[] encryptedEnvelope) {
        return (short) ByteBuffer.wrap(encryptedEnvelope).getInt();
    }
}
