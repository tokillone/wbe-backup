package com.licong.webbackup.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SiteLinkageMatcherTest {

    @Test
    void matchesExactSiteInsideTheSameLiterature() {
        var result = SiteLinkageMatcher.match(
                record("WBE1", "China", "Sichuan", "Chengdu", "WWTP1"),
                List.of(site("WBE1", "China", "Sichuan", "Chengdu", "WWTP1", "成都第一污水厂", "WBE1-S1", true)),
                "upload:1:row:2"
        );

        assertThat(result.status()).isEqualTo(SiteLinkageMatcher.STATUS_EXACT);
        assertThat(result.effectiveSiteKeys()).containsExactly("reported:WBE1-S1");
    }

    @Test
    void doesNotMatchSameNameAcrossLiteratures() {
        var result = SiteLinkageMatcher.match(
                record("WBE2", "China", "Sichuan", "Chengdu", "WWTP1"),
                List.of(site("WBE1", "China", "Sichuan", "Chengdu", "WWTP1", "", "WBE1-S1", true)),
                "upload:1:row:2"
        );

        assertThat(result.status()).isEqualTo(SiteLinkageMatcher.STATUS_UNMATCHED_COUNTRY);
        assertThat(result.effectiveSiteKeys()).isEmpty();
    }

    @Test
    void keepsConfirmedIdAsCandidateInsteadOfMerging() {
        var sites = List.of(
                new SiteLinkageMatcher.SiteRow(2, "WBE1", "", "China", "", "Chengdu",
                        "Plant A", "", "WBE1-S1", "CONF-1", true, "", "same address"),
                new SiteLinkageMatcher.SiteRow(3, "WBE2", "", "China", "", "Chengdu",
                        "Plant A", "", "WBE2-S1", "CONF-1", true, "", "same address")
        );

        assertThat(SiteLinkageMatcher.MERGE_CONFIRMED_CROSS_DOCUMENT_SITES).isFalse();
        assertThat(sites).extracting(SiteLinkageMatcher.SiteRow::effectiveSiteKey)
                .containsExactly("reported:WBE1-S1", "reported:WBE2-S1");
    }

    @Test
    void supportsOneRecordLinkedToMultipleSitesWithoutChangingIdentity() {
        var result = SiteLinkageMatcher.match(
                record("WBE1", "China", "Sichuan", "Chengdu", "unknown name"),
                List.of(
                        site("WBE1", "China", "Sichuan", "Chengdu", "Plant A", "", "WBE1-S1", true),
                        site("WBE1", "China", "Sichuan", "Chengdu", "Plant B", "", "WBE1-S2", true)
                ),
                "upload:1:row:2"
        );

        assertThat(result.status()).isEqualTo(SiteLinkageMatcher.STATUS_MULTI);
        assertThat(result.effectiveSiteKeys()).containsExactly("reported:WBE1-S1", "reported:WBE1-S2");
    }

    @Test
    void returnsExcludedWhenOnlyMatchingRowsAreNotCounted() {
        var result = SiteLinkageMatcher.match(
                record("WBE1", "China", "Sichuan", "Chengdu", "Plant A"),
                List.of(site("WBE1", "China", "Sichuan", "Chengdu", "Plant A", "", "WBE1-S1", false)),
                "upload:1:row:2"
        );

        assertThat(result.status()).isEqualTo(SiteLinkageMatcher.STATUS_EXCLUDED);
        assertThat(result.reportedSiteKeys()).containsExactly("WBE1-S1");
        assertThat(result.effectiveSiteKeys()).isEmpty();
    }

    @Test
    void normalizesLocationAliasesButDoesNotUseFuzzyNames() {
        assertThat(SiteLinkageMatcher.locationMatches("Tianjin / 天津", "Tianjing")).isTrue();
        assertThat(SiteLinkageMatcher.locationMatches("İstanbul", "Istanbul")).isTrue();
        assertThat(SiteLinkageMatcher.locationMatches("Chengdu", "Chengdu City")).isFalse();
    }

    private Map<String, String> record(String literature, String country, String province, String city, String name) {
        return Map.of(
                "文献编号", literature,
                "污水厂位置_国", country,
                "污水厂位置_省", province,
                "污水厂位置_市", city,
                "污水厂名称", name
        );
    }

    private SiteLinkageMatcher.SiteRow site(String literature,
                                            String country,
                                            String province,
                                            String city,
                                            String rawName,
                                            String canonicalName,
                                            String key,
                                            boolean included) {
        return new SiteLinkageMatcher.SiteRow(
                2, literature, "", country, province, city, rawName, canonicalName,
                key, "", included, included ? "" : "excluded", ""
        );
    }
}
