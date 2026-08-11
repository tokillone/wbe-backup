package com.licong.webbackup.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MapAdmin1SeedContractTest {

    @Test
    void generatedSeedContainsRepresentativeGlobalAdmin1RowsAndVersionedAliases() throws IOException {
        String seed = resourceText("db/map_admin1_geolocation_seed.sql");

        assertThat(seed)
                .contains("'unitedsofamerica|california'")
                .contains("'unitedsofamerica|kentucky'")
                .contains("'unitedsofamerica|nevada'")
                .contains("'unitedsofamerica|newyork'")
                .contains("'unitedsofamerica|pennsylvania'")
                .contains("'australia|queensland'")
                .contains("INSERT INTO geo_location_aliases")
                .contains("'world-admin1-boundary', 20260810");
    }

    @Test
    void refreshUsesUniqueAliasesAndKeepsUnmatchedRowsInReservedBucket() throws IOException {
        String refresh = resourceText("db/map_pndl_stats_refresh_v2.sql");

        assertThat(refresh)
                .contains("JOIN geo_location_aliases")
                .contains("|__unassigned__")
                .contains("UNASSIGNED_ADMIN1")
                .contains("UNASSIGNED_CITY")
                .contains("CONCAT(ga.geo_key, '|__unassigned__')")
                .contains("city_location.parent_geo_key = ga.geo_key")
                .contains("WHERE ga.geo_key IS NULL");
    }

    private String resourceText(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
