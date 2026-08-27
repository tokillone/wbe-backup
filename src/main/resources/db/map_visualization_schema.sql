-- Map visualization schema.
-- Run this once, then run map_geolocation_seed.sql, then run map_pndl_stats_refresh.sql.

CREATE TABLE IF NOT EXISTS confirmed_sites (
    confirmed_site_id VARCHAR(80) PRIMARY KEY,
    canonical_name VARCHAR(500),
    country VARCHAR(120) NOT NULL,
    province VARCHAR(120),
    city VARCHAR(120),
    detailed_address VARCHAR(500),
    latitude DECIMAL(12,8),
    longitude DECIMAL(12,8),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    confirmation_evidence TEXT NOT NULL,
    confirmed_by BIGINT,
    confirmed_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_confirmed_sites_country (country),
    KEY idx_confirmed_sites_coordinates (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工确认的真实污水厂主表';

CREATE TABLE IF NOT EXISTS reported_sites (
    reported_site_key CHAR(64) PRIMARY KEY,
    literature_code VARCHAR(255) NOT NULL,
    raw_plant_name VARCHAR(500),
    sampling_site_code VARCHAR(255),
    country VARCHAR(120),
    province VARCHAR(120),
    city VARCHAR(120),
    detailed_address VARCHAR(500),
    latitude DECIMAL(12,8),
    longitude DECIMAL(12,8),
    key_quality VARCHAR(24) NOT NULL,
    confirmed_site_id VARCHAR(80),
    confirmation_evidence TEXT,
    confirmed_by BIGINT,
    confirmed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_reported_sites_literature (literature_code),
    KEY idx_reported_sites_confirmed (confirmed_site_id),
    KEY idx_reported_sites_geo (country, province, city),
    CONSTRAINT fk_reported_sites_confirmed FOREIGN KEY (confirmed_site_id)
        REFERENCES confirmed_sites(confirmed_site_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献内报告的污水厂或采样点位';

CREATE TABLE IF NOT EXISTS reported_site_confirmation_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reported_site_key CHAR(64) NOT NULL,
    previous_confirmed_site_id VARCHAR(80),
    confirmed_site_id VARCHAR(80) NOT NULL,
    confirmation_evidence TEXT NOT NULL,
    reviewed_by BIGINT,
    reviewed_at DATETIME NOT NULL,
    upload_id BIGINT,
    action VARCHAR(24) NOT NULL DEFAULT 'CONFIRM',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_confirmation_reported (reported_site_key, reviewed_at),
    KEY idx_site_confirmation_confirmed (confirmed_site_id),
    CONSTRAINT fk_site_confirmation_reported FOREIGN KEY (reported_site_key)
        REFERENCES reported_sites(reported_site_key) ON DELETE RESTRICT,
    CONSTRAINT fk_site_confirmation_confirmed FOREIGN KEY (confirmed_site_id)
        REFERENCES confirmed_sites(confirmed_site_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨文献真实点位人工确认审计';

ALTER TABLE sampling_events
    ADD COLUMN reported_site_key CHAR(64) NULL COMMENT '文献内报告点位稳定键';
CREATE INDEX idx_sampling_events_reported_site ON sampling_events (reported_site_key);

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

CREATE TABLE IF NOT EXISTS geo_location_aliases (
    alias_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    level VARCHAR(16) NOT NULL,
    country_key VARCHAR(180) NOT NULL,
    alias_key VARCHAR(180) NOT NULL,
    geo_key VARCHAR(180) NOT NULL,
    source VARCHAR(80) NOT NULL,
    source_version INT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_geo_alias (level, country_key, alias_key),
    KEY idx_geo_alias_target (level, geo_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地理维表别名映射';

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
    refreshed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_map_filter (category, subcategory, biomarker_key, year_label, level, geo_key),
    KEY idx_map_target_filter (target_class, category, subcategory, biomarker_key, year_label, level, geo_key),
    KEY idx_map_geo (level, geo_key),
    KEY idx_map_value (pndl_geomean_mg_d_1000inh),
    KEY idx_map_median (pndl_median_mg_d_1000inh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图可视化PNDL预聚合表';

-- Supporting indexes for source-table refresh and detail drilldown.
CREATE INDEX idx_wastewater_plants_geo ON wastewater_plants (country, province, city);
CREATE INDEX idx_compounds_map_filter ON compounds (substance_category, substance_subclass, biomarker_cas);
