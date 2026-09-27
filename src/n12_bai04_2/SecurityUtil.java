package n12_bai04_2;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Lớp tiện ích mã hóa AES-128 nâng cao tính năng bảo mật khi truyền tải qua mạng
 */
public class SecurityUtil {

    private static final String ALGORITHM = "AES";
    // Khóa bí mật 16 byte (128-bit) cho AES
    private static final byte[] SECRET_KEY = "NetLabN12SecKey!".getBytes(StandardCharsets.UTF_8);

    /**
     * Mã hóa chuỗi văn bản bằng thuật toán AES-128
     */
    public static String encrypt(String valueToEnc) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY, ALGORITHM);
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] encValue = c.doFinal(valueToEnc.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encValue);
        } catch (Exception e) {
            return valueToEnc; // Fallback nếu lỗi
        }
    }

    /**
     * Giải mã chuỗi văn bản đã được mã hóa AES-128
     */
    public static String decrypt(String encryptedValue) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY, ALGORITHM);
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedValue = Base64.getDecoder().decode(encryptedValue);
            byte[] decValue = c.doFinal(decodedValue);
            return new String(decValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encryptedValue; // Fallback nếu lỗi
        }
    }
}
