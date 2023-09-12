package flightapp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * A collection of utility methods to help with managing passwords
 */
public class PasswordUtils {
  /**
   * Generates a cryptographically-secure salted password.
   */
  public static byte[] hashPassword(String password) {
    byte[] salt = generateSalt();
    byte[] saltedHash = generateSaltedPassword(password, salt);

    // combine the salt and salted hash into a single byte array that can be written to the database
    byte[] combined = new byte[salt.length + saltedHash.length];
    System.arraycopy(salt, 0, combined, 0, salt.length);
    System.arraycopy(saltedHash, 0, combined, salt.length, saltedHash.length);

    return combined;
  }


  /**
   * Verifies whether the plaintext password can be hashed to provided salted hashed password.
   */
  /**
   * Verifies whether the plaintext password can be hashed to provided salted hashed password.
   */
  public static boolean plaintextMatchesHash(String plaintext, byte[] saltedHashed) {
    // Extract the salt from the byte array
    byte[] salt = new byte[SALT_LENGTH];
    System.arraycopy(saltedHashed, 0, salt, 0, SALT_LENGTH);

    // Use the salt to salt the plaintext and check whether it matches the password hash
    try {
      byte[] saltedHash = generateSaltedPassword(plaintext, salt);
      byte[] storedSaltedHash = new byte[saltedHashed.length - SALT_LENGTH];
      System.arraycopy(saltedHashed, SALT_LENGTH, storedSaltedHash, 0, storedSaltedHash.length);
      return MessageDigest.isEqual(saltedHash, storedSaltedHash);
    } catch (IllegalStateException ex) {
      // If there's an exception during the hash generation, consider the match failed
      return false;
    }
  }


  // Password hashing parameter constants.
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;
  private static final int SALT_LENGTH = 16;

  /**
   * Generate a small bit of randomness to serve as a password "salt"
   */
  static byte[] generateSalt() {
    // TODO: implement this.
    byte[] salt = new byte[SALT_LENGTH];
    SecureRandom random = new SecureRandom();
    random.nextBytes(salt);
    return salt;
  }

  /**
   * Uses the provided salt to generate a cryptographically-secure hash of the provided password.
   * The resultant byte array should be KEY_LENGTH bytes long.
   */
  static byte[] generateSaltedPassword(String password, byte[] salt)
    throws IllegalStateException {
    // Specify the hash parameters, including the salt
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                                  HASH_STRENGTH, KEY_LENGTH * 8 /* length in bits */);

    // Hash the whole thing
    SecretKeyFactory factory = null;
    byte[] hash = null; 
    try {
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      hash = factory.generateSecret(spec).getEncoded();
      return hash;
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException();
    }
  }

}
