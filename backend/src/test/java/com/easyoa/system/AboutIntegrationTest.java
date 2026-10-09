package com.easyoa.system;

import com.easyoa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AboutIntegrationTest extends AbstractIntegrationTest {
    @Test
    void softwareMetadataIsPublicBeforeSetupAndContainsNoDeploymentSecrets() throws Exception {
        mockMvc.perform(get("/api/system/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edition").value("Community Edition"))
                .andExpect(jsonPath("$.data.license").value("AGPL-3.0-only"))
                .andExpect(jsonPath("$.data.releaseStatus").value("Development Build"))
                .andExpect(jsonPath("$.data.signatureVerified").value(false))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.sessionSecret").doesNotExist());
    }
    @Test
    void publicMetadataDoesNotOpenWriteRequestsOrOtherSystemResources() throws Exception {
        mockMvc.perform(post("/api/system/about")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/security/settings")).andExpect(status().isUnauthorized());
    }
}
