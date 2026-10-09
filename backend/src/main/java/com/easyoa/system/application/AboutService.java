package com.easyoa.system.application;

import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.easyoa.system.dto.AboutResponse;

@Service
public class AboutService {
    private static final String SOURCE = "https://github.com/YEXIAONAN/EasyOA";
    private final Environment environment;
    private final Properties build = new Properties();

    public AboutService(Environment environment) throws java.io.IOException {
        this.environment = environment;
        try (var stream = new ClassPathResource("easyoa-build.properties").getInputStream()) { build.load(stream); }
    }

    public AboutResponse about() {
        String version = build.getProperty("version", "unknown");
        boolean development = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean official = !development && ReleaseIdentityVerifier.verify(
                Path.of(environment.getProperty("EASYOA_RELEASE_METADATA_PATH", "/opt/easyoa-integrity")),
                Path.of(environment.getProperty("EASYOA_RELEASE_ARTIFACT_PATH", "/app/app.jar")),
                build.getProperty("release-key-fingerprint", ""), version);
        String source = SOURCE;
        String ref = build.getProperty("source-ref", "");
        if (ref.matches("v[0-9A-Za-z.-]+")) source += "/tree/" + ref;
        String customSource = environment.getProperty("EASYOA_SOURCE_URL", "");
        try {
            URI uri = URI.create(customSource);
            if ("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) source = uri.toString();
        } catch (IllegalArgumentException ignored) { }
        return new AboutResponse("Community Edition", version, "AGPL-3.0-only",
                official ? "YEXIAONAN" : "Local build", source,
                development ? "Development Build" : official ? "Official" : "Unofficial Build", official);
    }
}
