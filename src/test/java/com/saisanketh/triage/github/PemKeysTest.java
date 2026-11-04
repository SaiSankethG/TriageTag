package com.saisanketh.triage.github;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemKeysTest {

    /** A fresh test key (never a real one) as PKCS#8 bytes, and the PKCS#1 bytes inside it. */
    static KeyPair testKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** For a 2048-bit key, PKCS#8 = a 26-byte header + the PKCS#1 key (see PemKeys' class comment). */
    static byte[] pkcs1Of(KeyPair key) {
        byte[] pkcs8 = key.getPrivate().getEncoded();
        return Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
    }

    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    @Test
    void wrappingPkcs1GivesExactlyJavasOwnPkcs8Bytes() throws Exception {
        KeyPair key = testKey();

        assertThat(PemKeys.pkcs1ToPkcs8(pkcs1Of(key))).isEqualTo(key.getPrivate().getEncoded());
    }

    @Test
    void readsBothPemFormats() throws Exception {
        KeyPair key = testKey();

        assertThat(PemKeys.readRsaPrivateKey(pem("RSA PRIVATE KEY", pkcs1Of(key)))).isEqualTo(key.getPrivate());
        assertThat(PemKeys.readRsaPrivateKey(pem("PRIVATE KEY", key.getPrivate().getEncoded()))).isEqualTo(key.getPrivate());
    }

    @Test
    void derLengthsUseShortAndLongForm() {
        assertThat(PemKeys.derLength(5)).containsExactly(0x05);
        assertThat(PemKeys.derLength(200)).containsExactly(0x81, 0xc8);
        assertThat(PemKeys.derLength(1190)).containsExactly(0x82, 0x04, 0xa6);
    }

    @Test
    void somethingElseIsRejected() {
        assertThatThrownBy(() -> PemKeys.readRsaPrivateKey("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
