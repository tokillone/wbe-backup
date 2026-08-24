package com.licong.webbackup.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MapVisualizationSchemaInitializerTest {

    @Test
    void deletesLegacySpecialAdminCityAggregates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        MapVisualizationSchemaInitializer initializer =
                new MapVisualizationSchemaInitializer(jdbcTemplate, dataSource);

        initializer.deleteSpecialAdminCityStats();

        verify(jdbcTemplate).update(contains("china|hongkong|hongkong"));
        verify(jdbcTemplate).update(contains("china|aomen|macao"));
    }
}
