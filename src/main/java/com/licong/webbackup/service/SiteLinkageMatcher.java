package com.licong.webbackup.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Matches one measurement row to zero, one, or many workbook-reported sites. */
public final class SiteLinkageMatcher {

    public static final boolean MERGE_CONFIRMED_CROSS_DOCUMENT_SITES = false;

    public static final String STATUS_EXACT = "精确关联";
    public static final String STATUS_POSITION_FALLBACK = "位置字段回退匹配";
    public static final String STATUS_MULTI = "一条记录关联多个点位";
    public static final String STATUS_EXCLUDED = "关联记录不计数";
    public static final String STATUS_UNMATCHED_COUNTRY = "未匹配国家";
    public static final String STATUS_UNMATCHED = "未匹配";

    private static final Set<String> MISSING_TOKENS = Set.of(
            "", "-", "/", "na", "n/a", "n.a.", "nan", "none", "null",
            "未报道", "未报告", "未说明"
    );

    private SiteLinkageMatcher() {
    }

    public static MatchResult match(Map<String, String> record,
                                    List<SiteRow> allSites,
                                    String internalRecordKey) {
        String literatureCode = text(record.get("文献编号"));
        String country = text(record.get("污水厂位置_国"));
        String province = text(record.get("污水厂位置_省"));
        String city = text(record.get("污水厂位置_市"));
        String plantName = text(record.get("污水厂名称"));

        List<SiteRow> literatureCandidates = allSites.stream()
                .filter(site -> normalized(site.literatureCode()).equals(normalized(literatureCode)))
                .toList();
        if (country.isEmpty()) {
            return MatchResult.empty(internalRecordKey, STATUS_UNMATCHED_COUNTRY);
        }
        List<SiteRow> countryCandidates = literatureCandidates.stream()
                .filter(site -> normalized(site.country()).equals(normalized(country)))
                .toList();
        if (countryCandidates.isEmpty()) {
            return MatchResult.empty(internalRecordKey, STATUS_UNMATCHED_COUNTRY);
        }

        List<SiteRow> locationCandidates = narrowByLocation(countryCandidates, province, city);
        if (locationCandidates.isEmpty()) {
            return MatchResult.empty(internalRecordKey, STATUS_UNMATCHED);
        }

        List<SiteRow> namedCandidates = new ArrayList<>();
        if (!plantName.isEmpty()) {
            String normalizedName = normalized(plantName);
            for (SiteRow site : locationCandidates) {
                if (normalizedName.equals(normalized(site.rawPlantName()))
                        || normalizedName.equals(normalized(site.canonicalPlantName()))) {
                    namedCandidates.add(site);
                }
            }
        } else {
            List<SiteRow> includedAtLocation = locationCandidates.stream()
                    .filter(SiteRow::includeInPointCount)
                    .toList();
            if (includedAtLocation.size() == 1) {
                namedCandidates.add(includedAtLocation.getFirst());
            }
        }

        List<SiteRow> chosen = namedCandidates.isEmpty() ? locationCandidates : namedCandidates;
        List<SiteRow> included = chosen.stream().filter(SiteRow::includeInPointCount).toList();
        if (included.isEmpty()) {
            return new MatchResult(
                    internalRecordKey,
                    STATUS_EXCLUDED,
                    chosen.stream().map(SiteRow::reportedSiteKey).filter(key -> !key.isBlank()).toList(),
                    List.of()
            );
        }

        String status = included.size() > 1
                ? STATUS_MULTI
                : namedCandidates.isEmpty() ? STATUS_POSITION_FALLBACK : STATUS_EXACT;
        return new MatchResult(
                internalRecordKey,
                status,
                included.stream().map(SiteRow::reportedSiteKey).toList(),
                included.stream().map(SiteRow::effectiveSiteKey).toList()
        );
    }

    private static List<SiteRow> narrowByLocation(List<SiteRow> candidates, String province, String city) {
        List<SiteRow> narrowed = candidates;
        if (!city.isEmpty()) {
            narrowed = narrowed.stream().filter(site -> locationMatches(site.city(), city)).toList();
        }
        if (!province.isEmpty()) {
            narrowed = narrowed.stream().filter(site -> locationMatches(site.province(), province)).toList();
        }
        return narrowed;
    }

    static boolean locationMatches(String siteValue, String recordValue) {
        String recordKey = locationKey(recordValue);
        if (recordKey.isEmpty()) return false;
        for (String part : text(siteValue).split("/")) {
            if (recordKey.equals(locationKey(part))) return true;
        }
        return false;
    }

    public static boolean included(String value) {
        return Set.of("是", "yes", "y", "true", "1").contains(normalized(value));
    }

    public static String text(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        return MISSING_TOKENS.contains(normalized.toLowerCase(Locale.ROOT)) ? "" : normalized;
    }

    static String normalized(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String locationKey(String value) {
        String decomposed = Normalizer.normalize(text(value), Normalizer.Form.NFKD);
        StringBuilder compact = new StringBuilder();
        for (int offset = 0; offset < decomposed.length(); ) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (codePoint == 0x0131) codePoint = 'i';
            if (Character.isLetterOrDigit(codePoint)) {
                compact.appendCodePoint(Character.toLowerCase(codePoint));
            }
        }
        String key = compact.toString();
        return "tianjing".equals(key) ? "tianjin" : key;
    }

    public record SiteRow(
            int excelRow,
            String literatureCode,
            String doi,
            String country,
            String province,
            String city,
            String rawPlantName,
            String canonicalPlantName,
            String reportedSiteKey,
            String confirmedSiteId,
            boolean includeInPointCount,
            String siteNote,
            String confirmationEvidence
    ) {
        public SiteRow {
            literatureCode = text(literatureCode);
            doi = text(doi);
            country = text(country);
            province = text(province);
            city = text(city);
            rawPlantName = text(rawPlantName);
            canonicalPlantName = text(canonicalPlantName);
            reportedSiteKey = text(reportedSiteKey);
            confirmedSiteId = text(confirmedSiteId);
            siteNote = text(siteNote);
            confirmationEvidence = text(confirmationEvidence);
        }

        public String effectiveSiteKey() {
            return "reported:" + reportedSiteKey;
        }
    }

    public record MatchResult(
            String internalRecordKey,
            String status,
            List<String> reportedSiteKeys,
            List<String> effectiveSiteKeys
    ) {
        public MatchResult {
            reportedSiteKeys = List.copyOf(new LinkedHashSet<>(reportedSiteKeys));
            effectiveSiteKeys = List.copyOf(new LinkedHashSet<>(effectiveSiteKeys));
        }

        static MatchResult empty(String internalRecordKey, String status) {
            return new MatchResult(internalRecordKey, status, List.of(), List.of());
        }
    }
}
