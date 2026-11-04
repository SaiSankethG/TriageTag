package com.saisanketh.triage.github;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Reads an RSA private key from PEM text with plain java.security (no BouncyCastle).
 *
 * <p>GitHub gives App keys as PKCS#1 ("-----BEGIN RSA PRIVATE KEY-----"), but Java's KeyFactory only reads PKCS#8
 * ("-----BEGIN PRIVATE KEY-----"). PKCS#8 is just PKCS#1 wrapped in a small header that says "this is an RSA key", so we
 * add that header ourselves:
 * <pre>
 * SEQUENCE {
 *   INTEGER 0                                   -- version
 *   SEQUENCE { OID 1.2.840.113549.1.1.1, NULL } -- algorithm: rsaEncryption
 *   OCTET STRING { the PKCS#1 bytes }
 * }
 * </pre>
 */
final class PemKeys {

    /** DER bytes of: INTEGER 0, SEQUENCE { OID rsaEncryption, NULL }. */
    private static final byte[] VERSION_AND_ALGORITHM = {
            0x02, 0x01, 0x00,
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};

    private PemKeys() {
    }

    static PrivateKey readRsaPrivateKey(String pem) {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        if (!pkcs1 && !pem.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("not a PEM RSA private key (expected BEGIN RSA PRIVATE KEY or BEGIN PRIVATE KEY)");
        }
        String base64 = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs1ToPkcs8(der) : der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("could not read the RSA private key", e);
        }
    }

    static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(VERSION_AND_ALGORITHM);
        body.write(0x04);                         // OCTET STRING
        body.writeBytes(derLength(pkcs1.length));
        body.writeBytes(pkcs1);
        byte[] content = body.toByteArray();

        ByteArrayOutputStream pkcs8 = new ByteArrayOutputStream();
        pkcs8.write(0x30);                        // SEQUENCE
        pkcs8.writeBytes(derLength(content.length));
        pkcs8.writeBytes(content);
        return pkcs8.toByteArray();
    }

    /** DER length: one byte below 128; otherwise 0x80 + the number of length bytes, then the length, big-endian. */
    static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }
        int bytes = length > 0xffffff ? 4 : length > 0xffff ? 3 : length > 0xff ? 2 : 1;
        byte[] out = new byte[1 + bytes];
        out[0] = (byte) (0x80 | bytes);
        for (int i = 0; i < bytes; i++) {
            out[bytes - i] = (byte) (length >>> (8 * i));
        }
        return out;
    }
}
