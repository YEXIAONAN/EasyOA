package com.easyoa.system;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.easyoa.system.application.ReleaseIdentityVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseIdentityVerifierTest {
    @TempDir Path directory;
    private Path jar;
    private String fingerprint;
    private KeyPair key;

    @BeforeEach
    void signedFixture() throws Exception {
        key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        fingerprint = hash(key.getPublic().getEncoded());
        jar = directory.resolve("running.jar"); Files.writeString(jar, "synthetic jar fixture");
        Files.writeString(directory.resolve("release-public-key.pem"), "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
        Files.writeString(directory.resolve("manifest.sha256"), hash("1.2.3\n".getBytes(StandardCharsets.UTF_8))
                + "  VERSION\n" + hash(Files.readAllBytes(jar)) + "  backend/app.jar\n");
        sign();
    }

    void sign() throws Exception {
        var signer = Signature.getInstance("Ed25519"); signer.initSign(key.getPrivate());
        signer.update(Files.readAllBytes(directory.resolve("manifest.sha256")));
        Files.write(directory.resolve("manifest.sig"), signer.sign());
    }
    boolean verify() { return ReleaseIdentityVerifier.verify(directory, jar, fingerprint, "1.2.3"); }
    String hash(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }

    @Test void validSignedBuildIsOfficial() { assertThat(verify()).isTrue(); }
    @Test void modifiedRunningJarIsUnofficial() throws Exception { Files.writeString(jar, "modified"); assertThat(verify()).isFalse(); }
    @Test void missingSignatureIsUnofficial() throws Exception { Files.delete(directory.resolve("manifest.sig")); assertThat(verify()).isFalse(); }
    @Test void differentTrustedBuildKeyIsUnofficial() { fingerprint = "0".repeat(64); assertThat(verify()).isFalse(); }
    @Test void versionMismatchIsUnofficial() { assertThat(ReleaseIdentityVerifier.verify(directory, jar, fingerprint, "1.2.4")).isFalse(); }
    @Test void malformedOrDuplicateSignedManifestIsUnofficial() throws Exception {
        Path manifest = directory.resolve("manifest.sha256"); Files.writeString(manifest, Files.readString(manifest) + Files.readString(manifest)); sign(); assertThat(verify()).isFalse();
    }
}
