/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

/*
 * Tuya sharing transport adapted from tuya/tuya-device-sharing-sdk (MIT).
 * Copyright (c) 2023 Tuya. See THIRD_PARTY_LICENSES/tuya-device-sharing-sdk.txt.
 */
package com.nitramite.porssiohjain.services.tuya;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

final class TuyaSharingCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NONCE_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private TuyaSharingCodec() { }

    static String hashKey(String requestId, String refreshToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest((requestId + refreshToken).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Tuya hash unavailable", e); }
    }

    static String secret(String requestId, String hashKey) {
        return hmac(requestId, hashKey).substring(0, 16);
    }

    static String sign(String hashKey, Map<String, String> headers, String query, String body) {
        List<String> parts = new ArrayList<>();
        for (String key : List.of("X-appKey", "X-requestId", "X-sid", "X-time", "X-token")) {
            if (!headers.getOrDefault(key, "").isEmpty()) parts.add(key + "=" + headers.get(key));
        }
        return hmac(hashKey, String.join("||", parts) + query + body);
    }

    static String encrypt(String json, String secret) {
        byte[] nonce = new byte[12];
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte) NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length()));
        return encrypt(json, secret, nonce);
    }

    static String encrypt(String json, String secret, byte[] nonce) {
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, secret, nonce);
            return Base64.getEncoder().encodeToString(nonce)
                    + Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new TuyaException("tuya.error.transport"); }
    }

    static String decrypt(String encoded, String secret) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 28) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, secret, Arrays.copyOf(bytes, 12))
                    .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new TuyaException("tuya.error.transport"); }
    }

    private static Cipher cipher(int mode, String secret, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }

    private static String hmac(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Tuya signature unavailable", e); }
    }
}
