package io.github.toxicity188.ysmconverter.crypto;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * Standard AES-128-CBC decryption utility for YSM legacy V1 and V2 models.
 *
 * <pre>{@code
 * byte[] decrypted = AesCipher.decryptCbc(key, iv, encryptedData);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class AesCipher {

    private AesCipher() {
    }

    /**
     * Decrypts ciphertext using AES-128-CBC with no padding.
     *
     * @param key  the AES key (first 16 bytes are used)
     * @param iv   the 16-byte initialization vector
     * @param data the ciphertext (length must be multiple of 16)
     * @return the decrypted plaintext
     * @throws GeneralSecurityException if decryption fails
     */
    @CheckReturnValue
    public static byte @NotNull [] decryptCbc(byte @NotNull [] key, byte @NotNull [] iv, byte @NotNull [] data) throws GeneralSecurityException {
        var keySpec = new SecretKeySpec(key, 0, 16, "AES");
        var ivSpec = new IvParameterSpec(iv, 0, 16);
        var cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }
}
