-- Refresh map_pndl_stats from real backend tables.
-- Prerequisite: map_visualization_schema.sql and map_geolocation_seed.sql have been applied.

TRUNCATE TABLE map_pndl_stats;

INSERT INTO map_pndl_stats (
    level,
    geo_key,
    parent_geo_key,
    display_name,
    country,
    province,
    city,
    latitude,
    longitude,
    target_class,
    category,
    subcategory,
    biomarker_key,
    biomarker_label,
    biomarker_cas,
    year_label,
    pndl_geomean_mg_d_1000inh,
    pndl_mean_mg_d_1000inh,
    pndl_min_mg_d_1000inh,
    pndl_max_mg_d_1000inh,
    record_count,
    doi_count,
    year_count,
    city_count,
    point_count,
    pndl_sources,
    is_mappable,
    refreshed_at
)
WITH base AS (
    SELECT
      gl.level,
      gl.geo_key,
      gl.parent_geo_key,
      gl.display_name,
      gl.country,
      gl.province,
      gl.city,
      gl.latitude,
      gl.longitude,
      NULLIF(TRIM(c.target_category), '') AS target_class,
      NULLIF(TRIM(c.substance_category), '') AS category,
      COALESCE(NULLIF(TRIM(c.substance_subclass), ''), '未分类') AS source_subcategory,
      COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) AS source_biomarker_key,
      COALESCE(NULLIF(TRIM(c.biomarker_name), ''), c.drug_name) AS source_biomarker_label,
      NULLIF(TRIM(c.biomarker_cas), '') AS biomarker_cas,
      COALESCE(
        CASE WHEN LEFT(TRIM(se.sampling_start_ym), 4) REGEXP '^[0-9]{4}$' THEN LEFT(TRIM(se.sampling_start_ym), 4) END,
        CASE WHEN se.sample_collection_time IS NOT NULL THEN DATE_FORMAT(se.sample_collection_time, '%Y') END,
        '未标注年份'
      ) AS source_year,
      wp.plant_id,
      wp.city AS source_city,
      c.doi,
      m.measurement_id,
      CASE
        WHEN LOWER(COALESCE(m.plot_pndl_unit, m.pndl_unit, m.pndl_estimated_unit, '')) IN
          ('mg/day/1000 inh', 'mg/day/1000inh', 'mg/d/1000 inh', 'mg/d/1000inh',
           'mg/day/1000 people', 'mg/d/1000 people', 'mg/1000p/day', 'mg/1000 inh/day',
           'mg/day/1000 inhabitants')
          THEN COALESCE(m.plot_pndl_value, m.pndl_value, m.pndl_estimated_value)
        WHEN LOWER(COALESCE(m.plot_pndl_unit, m.pndl_unit, m.pndl_estimated_unit, '')) IN
          ('g/day/1000 inh', 'g/day/1000inh', 'g/day/1000 people', 'g/d/1000 people',
           'g/day/1000 inhabitants')
          THEN COALESCE(m.plot_pndl_value, m.pndl_value, m.pndl_estimated_value) * 1000
        ELSE NULL
      END AS pndl_mg_d_1000inh,
      CASE
        WHEN m.plot_pndl_value IS NOT NULL THEN '做图PNDL'
        WHEN m.pndl_value IS NOT NULL THEN 'PNDL'
        WHEN m.pndl_estimated_value IS NOT NULL THEN 'PNDL估算'
        ELSE NULL
      END AS pndl_source
    FROM measurements m
    JOIN compounds c ON c.compound_id = m.compound_id
    JOIN sampling_events se ON se.event_id = m.event_id
    JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
    JOIN geo_locations gl ON gl.is_mappable = TRUE
      AND (
        (gl.level = 'country'
          AND (LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
            OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.country), ' ', '_'), '-', '_'), '.', '')) = gl.geo_key))
        OR (gl.level = 'admin1'
          AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
          AND (LOWER(TRIM(wp.province)) = LOWER(TRIM(gl.province))
            OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.province), ' ', '_'), '-', '_'), '.', '')) = SUBSTRING_INDEX(gl.geo_key, '|', -1)))
        OR (gl.level = 'city'
          AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
          AND (LOWER(TRIM(wp.city)) = LOWER(TRIM(gl.city))
            OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.city), ' ', '_'), '-', '_'), '.', '')) = SUBSTRING_INDEX(gl.geo_key, '|', -1)))
      )
    WHERE NULLIF(TRIM(c.substance_category), '') IS NOT NULL
),
valid_base AS (
    SELECT *
    FROM base
    WHERE pndl_mg_d_1000inh IS NOT NULL
      AND pndl_mg_d_1000inh > 0
),
expanded AS (
    SELECT *, source_subcategory AS subcategory, source_biomarker_key AS biomarker_key,
      source_biomarker_label AS biomarker_label, source_year AS year_label
    FROM valid_base
    UNION ALL
    SELECT *, '全部小类', source_biomarker_key, source_biomarker_label, source_year
    FROM valid_base
    UNION ALL
    SELECT *, source_subcategory, 'ALL', '全部 biomarker', source_year
    FROM valid_base
    UNION ALL
    SELECT *, '全部小类', 'ALL', '全部 biomarker', source_year
    FROM valid_base
    UNION ALL
    SELECT *, source_subcategory, source_biomarker_key, source_biomarker_label, '全部年份'
    FROM valid_base
    UNION ALL
    SELECT *, '全部小类', source_biomarker_key, source_biomarker_label, '全部年份'
    FROM valid_base
    UNION ALL
    SELECT *, source_subcategory, 'ALL', '全部 biomarker', '全部年份'
    FROM valid_base
    UNION ALL
    SELECT *, '全部小类', 'ALL', '全部 biomarker', '全部年份'
    FROM valid_base
)
SELECT
    level,
    geo_key,
    parent_geo_key,
    display_name,
    country,
    province,
    city,
    latitude,
    longitude,
    MIN(target_class) AS target_class,
    category,
    subcategory,
    biomarker_key,
    biomarker_label,
    CASE WHEN biomarker_key = 'ALL' THEN NULL ELSE MIN(biomarker_cas) END AS biomarker_cas,
    year_label,
    EXP(AVG(LN(pndl_mg_d_1000inh))) AS pndl_geomean_mg_d_1000inh,
    AVG(pndl_mg_d_1000inh) AS pndl_mean_mg_d_1000inh,
    MIN(pndl_mg_d_1000inh) AS pndl_min_mg_d_1000inh,
    MAX(pndl_mg_d_1000inh) AS pndl_max_mg_d_1000inh,
    COUNT(DISTINCT measurement_id) AS record_count,
    COUNT(DISTINCT NULLIF(TRIM(doi), '')) AS doi_count,
    COUNT(DISTINCT CASE WHEN source_year <> '未标注年份' THEN source_year END) AS year_count,
    COUNT(DISTINCT NULLIF(TRIM(source_city), '')) AS city_count,
    COUNT(DISTINCT plant_id) AS point_count,
    GROUP_CONCAT(DISTINCT pndl_source ORDER BY pndl_source SEPARATOR '、') AS pndl_sources,
    TRUE AS is_mappable,
    NOW() AS refreshed_at
FROM expanded
GROUP BY
    level,
    geo_key,
    parent_geo_key,
    display_name,
    country,
    province,
    city,
    latitude,
    longitude,
    category,
    subcategory,
    biomarker_key,
    biomarker_label,
    year_label;
