package io.github.pokemeetup.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class PasswordUtils {
    private static final int SALT_LENGTH = 16; // 16 bytes
    private static final int HASH_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256; // 256 bits

    /**
     * Generates a salted PBKDF2 hash of the given password.
     *
     * @param password The password to hash.
     * @return The hashed password in the format "salt:hash".
     * @throws Exception If hashing fails.
     */

    /**
     * Verifies a password against the stored hash.
     *
     * @param password       The password to verify.
     * @return True if the password matches; false otherwise.
     * @throws Exception If verification fails.
     */
    public static String hashPassword(String password) {
        try {
            return BCrypt.withDefaults().hashToString(12, password.toCharArray());
        } catch (Exception e) {
            GameLogger.info("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyPassword(String plainPassword, String storedHash) {
        try {
            if (storedHash == null) {
                GameLogger.info("Stored hash is null for password verification");
                return false;
            }
            return BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash).verified;
        } catch (Exception e) {
            GameLogger.info("Error verifying password: " + e.getMessage());
            return false;
        }
    }


    /**
     * Compares two byte arrays in length-constant time to prevent timing attacks.
     *
     * @param a First byte array.
     * @param b Second byte array.
     * @return True if both arrays are equal; false otherwise.
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
