-- Map visualization schema.
-- Run this once, then run map_geolocation_seed.sql, then run map_pndl_stats_refresh.sql.

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
    UNIQUE KEY uk_geo_level_key (level, geo_key),
    KEY idx_geo_parent (level, parent_geo_key),
    KEY idx_geo_names (country, province, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图可视化地理维表';

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
    pndl_geomean_mg_d_1000inh DECIMAL(28,10),
    pndl_mean_mg_d_1000inh DECIMAL(28,10),
    pndl_min_mg_d_1000inh DECIMAL(28,10),
    pndl_max_mg_d_1000inh DECIMAL(28,10),
    record_count BIGINT NOT NULL DEFAULT 0,
    doi_count BIGINT NOT NULL DEFAULT 0,
    year_count BIGINT NOT NULL DEFAULT 0,
    city_count BIGINT NOT NULL DEFAULT 0,
    point_count BIGINT NOT NULL DEFAULT 0,
    pndl_sources VARCHAR(200),
    is_mappable BOOLEAN NOT NULL DEFAULT TRUE,
    refreshed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_map_filter (category, subcategory, biomarker_key, year_label, level, geo_key),
    KEY idx_map_target_filter (target_class, category, subcategory, biomarker_key, year_label, level, geo_key),
    KEY idx_map_geo (level, geo_key),
    KEY idx_map_value (pndl_geomean_mg_d_1000inh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图可视化PNDL预聚合表';

-- Supporting indexes for source-table refresh and detail drilldown.
CREATE INDEX idx_wastewater_plants_geo ON wastewater_plants (country, province, city);
CREATE INDEX idx_compounds_map_filter ON compounds (substance_category, substance_subclass, biomarker_cas);
