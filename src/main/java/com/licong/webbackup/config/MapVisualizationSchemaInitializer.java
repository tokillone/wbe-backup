package com.licong.webbackup.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@DependsOn("dataUploadSchemaInitializer")
public class MapVisualizationSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public MapVisualizationSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        ensureTables();
        ensureIndex("geo_locations", "idx_geo_parent", "CREATE INDEX idx_geo_parent ON geo_locations (level, parent_geo_key)");
        ensureIndex("geo_locations", "idx_geo_names", "CREATE INDEX idx_geo_names ON geo_locations (country, province, city)");
        ensureColumn("map_pndl_stats", "pndl_median_mg_d_1000inh", "DECIMAL(28,10)");
        ensureColumn("map_pndl_stats", "biomarker_count", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("map_pndl_stats", "pndl_record_count", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("map_pndl_stats", "pndl_doi_count", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("map_pndl_stats", "pndl_point_count", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("map_pndl_stats", "pndl_year_count", "BIGINT NOT NULL DEFAULT 0");
        ensureIndex("map_pndl_stats", "idx_map_filter", "CREATE INDEX idx_map_filter ON map_pndl_stats (category, subcategory, biomarker_key, year_label, level, geo_key)");
        ensureIndex("map_pndl_stats", "idx_map_target_filter", "CREATE INDEX idx_map_target_filter ON map_pndl_stats (target_class, category, subcategory, biomarker_key, year_label, level, geo_key)");
        ensureIndex("map_pndl_stats", "idx_map_geo", "CREATE INDEX idx_map_geo ON map_pndl_stats (level, geo_key)");
        ensureIndex("map_pndl_stats", "idx_map_value", "CREATE INDEX idx_map_value ON map_pndl_stats (pndl_geomean_mg_d_1000inh)");
        ensureIndex("map_pndl_stats", "idx_map_median", "CREATE INDEX idx_map_median ON map_pndl_stats (pndl_median_mg_d_1000inh)");
        ensureIndex("wastewater_plants", "idx_wastewater_plants_geo", "CREATE INDEX idx_wastewater_plants_geo ON wastewater_plants (country, province, city)");
        ensureIndex("compounds", "idx_compounds_map_filter", "CREATE INDEX idx_compounds_map_filter ON compounds (substance_category, substance_subclass, biomarker_cas)");
        seedGeoLocationsIfEmpty();
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS geo_locations (
                    geo_location_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    level VARCHAR(16) NOT NULL,
                    geo_key VARCHAR(180) NOT NULL,
                    parent_geo_key VARCHAR(180),
                    country VARCHAR(120),
                    province VARCHAR(120),
                    city VARCHAR(120),
                    display_name VARCHAR(180) NOT NULL,
                    latitude DECIMAL(12,7),
                    longitude DECIMAL(12,7),
                    is_mappable BOOLEAN NOT NULL DEFAULT TRUE,
                    coordinate_source VARCHAR(80),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_geo_level_key (level, geo_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图可视化地理维表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS map_pndl_stats (
                    stat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    level VARCHAR(16) NOT NULL,
                    geo_key VARCHAR(180) NOT NULL,
                    parent_geo_key VARCHAR(180),
                    display_name VARCHAR(180) NOT NULL,
                    country VARCHAR(120),
                    province VARCHAR(120),
                    city VARCHAR(120),
                    latitude DECIMAL(12,7),
                    longitude DECIMAL(12,7),
                    target_class VARCHAR(120),
                    category VARCHAR(120) NOT NULL,
                    subcategory VARCHAR(120) NOT NULL,
                    biomarker_key VARCHAR(80) NOT NULL,
                    biomarker_label VARCHAR(220) NOT NULL,
                    biomarker_cas VARCHAR(80),
                    year_label VARCHAR(32) NOT NULL,
                    pndl_median_mg_d_1000inh DECIMAL(28,10),
                    pndl_geomean_mg_d_1000inh DECIMAL(28,10),
                    pndl_mean_mg_d_1000inh DECIMAL(28,10),
                    pndl_min_mg_d_1000inh DECIMAL(28,10),
                    pndl_max_mg_d_1000inh DECIMAL(28,10),
                    record_count BIGINT NOT NULL DEFAULT 0,
                    doi_count BIGINT NOT NULL DEFAULT 0,
                    year_count BIGINT NOT NULL DEFAULT 0,
                    city_count BIGINT NOT NULL DEFAULT 0,
                    point_count BIGINT NOT NULL DEFAULT 0,
                    biomarker_count BIGINT NOT NULL DEFAULT 0,
                    pndl_record_count BIGINT NOT NULL DEFAULT 0,
                    pndl_doi_count BIGINT NOT NULL DEFAULT 0,
                    pndl_point_count BIGINT NOT NULL DEFAULT 0,
                    pndl_year_count BIGINT NOT NULL DEFAULT 0,
                    pndl_sources VARCHAR(200),
                    is_mappable BOOLEAN NOT NULL DEFAULT TRUE,
                    refreshed_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图可视化PNDL预聚合表'
                """);
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        if (tableCount == null || tableCount == 0) {
            return;
        }
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        if (columnCount == null || columnCount == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        if (tableCount == null || tableCount == 0) {
            return;
        }
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        if (indexCount != null && indexCount > 0) {
            return;
        }
        jdbcTemplate.execute(createSql);
    }

    private void seedGeoLocationsIfEmpty() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geo_locations", Long.class);
        Long canonicalChinaCityCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM geo_locations
                WHERE level = 'city' AND geo_key = 'china|guangdong|guangzhou'
                """, Long.class);
        if (count != null && count > 0 && canonicalChinaCityCount != null && canonicalChinaCityCount > 0) {
            return;
        }
        ClassPathResource script = new ClassPathResource("db/map_geolocation_seed.sql");
        if (!script.exists()) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(script);
        populator.execute(dataSource);
        jdbcTemplate.update("""
                DELETE FROM geo_locations
                WHERE level IN ('admin1', 'city')
                  AND geo_key LIKE 'china|%'
                  AND COALESCE(coordinate_source, '') != 'canonical-boundary-centroid'
                """);
        if (count != null && count > 0) {
            ClassPathResource refreshScript = new ClassPathResource("db/map_pndl_stats_refresh.sql");
            if (refreshScript.exists()) {
                ResourceDatabasePopulator refreshPopulator = new ResourceDatabasePopulator();
                refreshPopulator.addScript(refreshScript);
                refreshPopulator.execute(dataSource);
            }
        }
    }
}
