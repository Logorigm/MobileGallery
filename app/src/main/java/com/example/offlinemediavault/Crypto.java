package com.example.offlinemediavault;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Шифрование файлов: AES-256-CTR + HMAC-SHA256 (целостность). Формат: "OMV1" | IV(16) | шифртекст | MAC(32).
 * Мастер-ключ случайный; хранится в SharedPreferences, обёрнутый ключом Android Keystore (аппаратным, если есть).
 */
final class Crypto {
    private static final byte[] MAGIC = {'O', 'M', 'V', '1'};
    private static final String KS = "AndroidKeyStore", ALIAS = "omv_master_wrap";
    private static byte[] master;

    private Crypto() {}

    static boolean hasMagic(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[4];
            if (in.read(b) != 4) return false;
            return b[0] == MAGIC[0] && b[1] == MAGIC[1] && b[2] == MAGIC[2] && b[3] == MAGIC[3];
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] wrap(byte[] m) {
        try {
            KeyStore ks = KeyStore.getInstance(KS);
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
                kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build());
                kg.generateKey();
            }
            SecretKey k = (SecretKey) ks.getKey(ALIAS, null);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(m);
            byte[] out = new byte[1 + iv.length + ct.length];
            out[0] = 1;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(ct, 0, out, 1 + iv.length, ct.length);
            return out;
        } catch (Throwable e) {
            byte[] out = new byte[1 + m.length];   // запасной вариант без Keystore
            System.arraycopy(m, 0, out, 1, m.length);
            return out;
        }
    }

    private static byte[] unwrap(byte[] raw) throws Exception {
        if (raw[0] == 0) {
            byte[] m = new byte[raw.length - 1];
            System.arraycopy(raw, 1, m, 0, m.length);
            return m;
        }
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        SecretKey k = (SecretKey) ks.getKey(ALIAS, null);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, raw, 1, 12));
        return c.doFinal(raw, 13, raw.length - 13);
    }

    private static synchronized byte[] master(Context ctx) throws Exception {
        if (master != null) return master;
        SharedPreferences p = ctx.getSharedPreferences("omv_secure", Context.MODE_PRIVATE);
        String s = p.getString("mk", null);
        if (s != null) {
            master = unwrap(Base64.decode(s, Base64.NO_WRAP));
            return master;
        }
        byte[] m = new byte[32];
        new SecureRandom().nextBytes(m);
        p.edit().putString("mk", Base64.encodeToString(wrap(m), Base64.NO_WRAP)).commit();
        master = m;
        return m;
    }

    private static byte[] derive(byte[] m, String label) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(m, "HmacSHA256"));
        return mac.doFinal(label.getBytes("UTF-8"));
    }

    static void encrypt(Context ctx, InputStream in, OutputStream out) throws Exception {
        byte[] m = master(ctx);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derive(m, "omv-enc"), "AES"), new IvParameterSpec(iv));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(derive(m, "omv-mac"), "HmacSHA256"));
        out.write(MAGIC);
        out.write(iv);
        mac.update(iv);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            byte[] enc = c.update(buf, 0, n);
            if (enc != null) { out.write(enc); mac.update(enc); }
        }
        byte[] last = c.doFinal();
        if (last != null && last.length > 0) { out.write(last); mac.update(last); }
        out.write(mac.doFinal());
        out.flush();
    }

    /** Расшифровывает в out; в конце проверяет MAC и бросает IOException("mac"), если файл повреждён. */
    static void decrypt(Context ctx, File src, OutputStream out) throws Exception {
        byte[] m = master(ctx);
        long total = src.length();
        if (total < 4 + 16 + 32) throw new IOException("short");
        try (InputStream in = new FileInputStream(src)) {
            byte[] head = new byte[20];
            readFully(in, head);
            for (int i = 0; i < 4; i++) if (head[i] != MAGIC[i]) throw new IOException("magic");
            byte[] iv = new byte[16];
            System.arraycopy(head, 4, iv, 0, 16);
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derive(m, "omv-enc"), "AES"), new IvParameterSpec(iv));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derive(m, "omv-mac"), "HmacSHA256"));
            mac.update(iv);
            long left = total - 20 - 32;
            byte[] buf = new byte[65536];
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("eof");
                left -= n;
                mac.update(buf, 0, n);
                if (out != null) {
                    byte[] dec = c.update(buf, 0, n);
                    if (dec != null) out.write(dec);
                }
            }
            byte[] tag = new byte[32];
            readFully(in, tag);
            if (!MessageDigest.isEqual(tag, mac.doFinal())) throw new IOException("mac");
            if (out != null) out.flush();
        }
    }

    static boolean verify(Context ctx, File src) {
        try {
            decrypt(ctx, src, null);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new IOException("eof");
            off += n;
        }
    }
}
