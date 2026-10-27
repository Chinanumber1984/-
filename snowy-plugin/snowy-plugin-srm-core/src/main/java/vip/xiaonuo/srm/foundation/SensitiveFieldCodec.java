package vip.xiaonuo.srm.foundation;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** SM4-CBC with random IV and encrypt-then-MAC (HMAC-SM3). Keys are injected, never generated as a runtime fallback. */
public final class SensitiveFieldCodec {
    private static final Provider BC = new BouncyCastleProvider();
    private final String keyId;
    private final byte[] encryptionKey, macKey, indexKey;
    private final SecureRandom random = new SecureRandom();
    public SensitiveFieldCodec(String keyId, byte[] encryptionKey, byte[] macKey, byte[] indexKey) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,32}") || encryptionKey == null || encryptionKey.length != 16
                || macKey == null || macKey.length < 32 || indexKey == null || indexKey.length < 32)
            throw new IllegalArgumentException("Explicit key id and independent SM4/MAC/index keys required");
        if (Arrays.equals(macKey,indexKey)) throw new IllegalArgumentException("MAC and search keys must be independent");
        this.keyId=keyId; this.encryptionKey=encryptionKey.clone(); this.macKey=macKey.clone(); this.indexKey=indexKey.clone();
    }
    private byte[] mac(byte[] key, String value) {
        try { Mac m=Mac.getInstance("HMACSM3",BC); m.init(new SecretKeySpec(key,"HMACSM3")); return m.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("SM3 unavailable",e); }
    }
    public String encrypt(String value) {
        Objects.requireNonNull(value);
        try {
            byte[] iv=new byte[16]; random.nextBytes(iv);
            Cipher c=Cipher.getInstance("SM4/CBC/PKCS7Padding",BC);
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"SM4"),new IvParameterSpec(iv));
            var enc=Base64.getUrlEncoder().withoutPadding();
            String body="v1."+keyId+"."+enc.encodeToString(iv)+"."+enc.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));
            return body+"."+enc.encodeToString(mac(macKey,body));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("SM4 unavailable",e); }
    }
    public String decrypt(String envelope) {
        try {
            String[] s=envelope.split("\\.",-1);
            if (s.length!=5 || !s[0].equals("v1") || !s[1].equals(keyId)) throw new IllegalArgumentException();
            String body=String.join(".",Arrays.copyOf(s,4));
            var dec=Base64.getUrlDecoder();
            if (!MessageDigest.isEqual(mac(macKey,body),dec.decode(s[4]))) throw new IllegalArgumentException();
            byte[] iv=dec.decode(s[2]); if(iv.length!=16) throw new IllegalArgumentException();
            Cipher c=Cipher.getInstance("SM4/CBC/PKCS7Padding",BC);
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"SM4"),new IvParameterSpec(iv));
            return new String(c.doFinal(dec.decode(s[3])),StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid encrypted field");
        }
    }
    public String creditFingerprint(String orgId, String creditCode) {
        if (orgId==null || !orgId.matches("[A-Za-z0-9_-]{1,64}") || creditCode==null || creditCode.isBlank())
            throw new IllegalArgumentException("Organization and credit code required");
        return HexFormat.of().formatHex(mac(indexKey,orgId+"\n"+creditCode.trim().toUpperCase(Locale.ROOT)));
    }
}
