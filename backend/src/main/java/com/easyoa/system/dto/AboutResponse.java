package com.easyoa.system.dto;

public record AboutResponse(String edition, String version, String license, String publisher,
        String sourceUrl, String releaseStatus, boolean signatureVerified) { }
