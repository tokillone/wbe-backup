package com.licong.webbackup.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportedSiteIdentityTest {

    @Test
    void sameLiteratureAndSiteNormalizeToSameKey() {
        ReportedSiteIdentity.Identity first = ReportedSiteIdentity.create(
                "LIT-01", "中国", "四川省", "成都市", "WWTP 1", "Plant A");
        ReportedSiteIdentity.Identity second = ReportedSiteIdentity.create(
                " lit-01 ", "中国", "四川省", "成都市", "wwtp   1", "Other name");

        assertThat(second.reportedSiteKey()).isEqualTo(first.reportedSiteKey());
        assertThat(first.keyQuality()).isEqualTo(ReportedSiteIdentity.QUALITY_IDENTIFIED);
    }

    @Test
    void sameNameAcrossLiteraturesRemainsIndependent() {
        String first = ReportedSiteIdentity.create(
                "LIT-01", "中国", "四川省", "成都市", null, "WWTP1").reportedSiteKey();
        String second = ReportedSiteIdentity.create(
                "LIT-02", "中国", "四川省", "成都市", null, "WWTP1").reportedSiteKey();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void missingSiteIdentifierGroupsWithinLiteratureAndGeography() {
        ReportedSiteIdentity.Identity first = ReportedSiteIdentity.create(
                "LIT-01", "中国", "四川省", "成都市", "NA", "NA");
        ReportedSiteIdentity.Identity second = ReportedSiteIdentity.create(
                "LIT-01", "中国", "四川省", "成都市", null, null);

        assertThat(second.reportedSiteKey()).isEqualTo(first.reportedSiteKey());
        assertThat(first.keyQuality()).isEqualTo(ReportedSiteIdentity.QUALITY_UNSPECIFIED);
    }

    @Test
    void confirmedIdentityOverridesReportedIdentityOnlyAfterAssignment() {
        assertThat(ReportedSiteIdentity.finalIdentityKey("abc", null)).isEqualTo("R:abc");
        assertThat(ReportedSiteIdentity.finalIdentityKey("abc", "SITE-001")).isEqualTo("C:SITE-001");
    }
}
