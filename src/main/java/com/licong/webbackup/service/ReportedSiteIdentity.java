package com.licong.webbackup.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class ReportedSiteIdentity {

    public static final String QUALITY_IDENTIFIED = "IDENTIFIED";
    public static final String QUALITY_UNSPECIFIED = "UNSPECIFIED";

    private static final String UNSPECIFIED_SITE = "__unspecified__";
    private static final Set<String> MISSING_TOKENS = Set.of(
            "na", "n/a", "n.a.", "none", "null", "unknown", "not reported", "not available",
            "未报告", "未提供", "未知", "不详", "无"
    );

    private ReportedSiteIdentity() {
    }

    public static Identity create(String literatureCode,
                                  String country,
                                  String province,
                                  String city,
                                  String samplingSiteCode,
                                  String plantName) {
        String normalizedCode = normalizeRequired(literatureCode, "__missing_literature__");
        String normalizedCountry = normalizeRequired(country, "__missing_country__");
        String normalizedProvince = normalizeRequired(province, "__missing_province__");
        String normalizedCity = normalizeRequired(city, "__missing_city__");
        String siteIdentifier = useful(samplingSiteCode)
                ? normalize(samplingSiteCode)
                : useful(plantName) ? normalize(plantName) : UNSPECIFIED_SITE;
        String quality = UNSPECIFIED_SITE.equals(siteIdentifier) ? QUALITY_UNSPECIFIED : QUALITY_IDENTIFIED;
        String material = String.join("\u001F",
                normalizedCode,
                normalizedCountry,
                normalizedProvince,
                normalizedCity,
                siteIdentifier);
        return new Identity(sha256(material), quality, siteIdentifier);
    }

    public static String finalIdentityKey(String reportedSiteKey, String confirmedSiteId) {
        return useful(confirmedSiteId) ? "C:" + confirmedSiteId.trim() : "R:" + reportedSiteKey;
    }

    public static boolean useful(String value) {
        if (value == null) return false;
        String normalized = normalize(value);
        return !normalized.isBlank() && !MISSING_TOKENS.contains(normalized);
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value, String fallback) {
        return useful(value) ? normalize(value) : fallback;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Identity(String reportedSiteKey, String keyQuality, String normalizedSiteIdentifier) {
    }
}
