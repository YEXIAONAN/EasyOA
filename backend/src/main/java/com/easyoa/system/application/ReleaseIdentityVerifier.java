package com.easyoa.system.application;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** Bind public release metadata to the trusted build key and the actual running jar. */
public final class ReleaseIdentityVerifier {
    private static final Pattern LINE = Pattern.compile("([0-9a-f]{64})  ([^\\r\\n]+)");
    private ReleaseIdentityVerifier() { }

    public static boolean verify(Path directory, Path jar, String fingerprint, String version) {
        if (!fingerprint.matches("[0-9a-f]{64}")) return false;
        try {
            byte[] manifest = Files.readAllBytes(directory.resolve("manifest.sha256"));
            if (manifest.length == 0 || manifest.length > 1024 * 1024) return false;
            String pem = Files.readString(directory.resolve("release-public-key.pem"), StandardCharsets.US_ASCII);
            byte[] der = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", ""));
            if (!hash(der).equals(fingerprint)) return false;
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key); verifier.update(manifest);
            if (!verifier.verify(Files.readAllBytes(directory.resolve("manifest.sig")))) return false;
            Map<String, String> hashes = new HashMap<>();
            for (String line : new String(manifest, StandardCharsets.UTF_8).split("\n")) {
                var match = LINE.matcher(line);
                if (!match.matches() || hashes.putIfAbsent(match.group(2), match.group(1)) != null) return false;
            }
            if (!hash((version + "\n").getBytes(StandardCharsets.UTF_8)).equals(hashes.get("VERSION"))) return false;
            try (InputStream input = Files.newInputStream(jar)) {
                var digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[64 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                return HexFormat.of().formatHex(digest.digest()).equals(hashes.get("backend/app.jar"));
            }
        } catch (Exception ignored) {
            // Return an honest unverified state; never expose filesystem paths or key material.
            return false;
        }
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
