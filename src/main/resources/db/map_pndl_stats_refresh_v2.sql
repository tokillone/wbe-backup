-- Point-association-table driven map refresh.
-- Temporary tables materialize expensive stages so MySQL does not expand the same CTE repeatedly.

DROP TEMPORARY TABLE IF EXISTS map_refresh_matched;
DROP TEMPORARY TABLE IF EXISTS map_refresh_variants;
DROP TEMPORARY TABLE IF EXISTS map_refresh_expanded;
DROP TEMPORARY TABLE IF EXISTS map_refresh_points;
DROP TEMPORARY TABLE IF EXISTS map_refresh_observations;

CREATE TEMPORARY TABLE map_refresh_matched ENGINE=InnoDB AS
WITH source_rows AS (
    SELECT
      CASE
        WHEN LOWER(REGEXP_REPLACE(COALESCE(TRIM(rs.country), ''), '[^0-9a-zA-Z]+', '')) IN
          ('unitedstates', 'unitedstatesofamerica', 'usa', 'us') THEN 'unitedsofamerica'
        WHEN LOWER(REGEXP_REPLACE(COALESCE(TRIM(rs.country), ''), '[^0-9a-zA-Z]+', '')) = 'czechrepublic'
          THEN 'czechia'
        ELSE LOWER(REGEXP_REPLACE(COALESCE(TRIM(rs.country), ''), '[^0-9a-zA-Z]+', ''))
      END AS country_key,
      LOWER(REGEXP_REPLACE(COALESCE(TRIM(rs.province), ''), '[^0-9a-zA-Z]+', '')) AS province_key,
      LOWER(REGEXP_REPLACE(COALESCE(TRIM(rs.city), ''), '[^0-9a-zA-Z]+', '')) AS city_key,
      COALESCE(NULLIF(TRIM(c.target_category), ''), '未分类') AS target_class,
      NULLIF(TRIM(c.substance_category), '') AS category,
      COALESCE(NULLIF(TRIM(c.substance_subclass), ''), '未分类') AS source_subcategory,
      COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) AS source_biomarker_key,
      COALESCE(NULLIF(TRIM(c.biomarker_name), ''), c.drug_name) AS source_biomarker_label,
      NULLIF(TRIM(c.biomarker_cas), '') AS biomarker_cas,
      COALESCE(
        CASE WHEN LEFT(TRIM(se.sampling_start_ym), 4) REGEXP '^[0-9]{4}$'
          THEN LEFT(TRIM(se.sampling_start_ym), 4) END,
        CASE WHEN se.sample_collection_time IS NOT NULL
          THEN DATE_FORMAT(se.sample_collection_time, '%Y') END,
        '未标注年份'
      ) AS source_year,
      b.effective_site_key AS site_identity_key,
      rs.city AS source_city,
      COALESCE(NULLIF(TRIM(rs.doi), ''), NULLIF(TRIM(c.doi), '')) AS doi,
      m.measurement_id,
      m.plot_pndl_value AS raw_pndl_value,
      LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        COALESCE(m.plot_pndl_unit, ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) AS unit_key,
      CASE WHEN m.plot_pndl_value IS NOT NULL THEN '做图PNDL' END AS pndl_source
    FROM measurements m
    JOIN compounds c ON c.compound_id = m.compound_id
    JOIN sampling_events se ON se.event_id = m.event_id
    JOIN record_site_bridge b ON b.measurement_id = m.measurement_id
      AND b.effective_site_key IS NOT NULL
    JOIN reported_sites rs ON rs.reported_site_key = b.reported_site_key
      AND rs.include_in_point_count = TRUE
    WHERE NULLIF(TRIM(c.substance_category), '') IS NOT NULL
),
converted_rows AS (
    SELECT source_rows.*,
      CASE
        WHEN unit_key REGEXP '^mg/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
          OR unit_key REGEXP '^mg/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
          THEN raw_pndl_value
        WHEN unit_key REGEXP '^mg/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
          THEN raw_pndl_value * 1000
        WHEN unit_key REGEXP '^g/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
          OR unit_key REGEXP '^g/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
          THEN raw_pndl_value * 1000
        WHEN unit_key REGEXP '^g/(day|d)/(10000(inh|inhabitants|people|persons|person|capita|p|pop))$'
          THEN raw_pndl_value * 100
        WHEN unit_key REGEXP '^ug/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
          OR unit_key REGEXP '^ug/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
          THEN raw_pndl_value / 1000
        WHEN unit_key REGEXP '^ug/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
          OR unit_key REGEXP '^ug/(inh|inhabitant|person|people|persons|capita)/(day|d).*$'
          THEN raw_pndl_value
        ELSE NULL
      END AS pndl_mg_d_1000inh
    FROM source_rows
)
SELECT gl.level, gl.geo_key, gl.parent_geo_key, gl.display_name,
  gl.country, gl.province, gl.city, gl.latitude, gl.longitude,
  converted_rows.target_class, converted_rows.category, converted_rows.source_subcategory,
  converted_rows.source_biomarker_key, converted_rows.source_biomarker_label,
  converted_rows.biomarker_cas, converted_rows.source_year,
  converted_rows.site_identity_key, converted_rows.source_city, converted_rows.doi,
  converted_rows.measurement_id, converted_rows.pndl_mg_d_1000inh, converted_rows.pndl_source
FROM converted_rows
JOIN geo_locations gl ON gl.level = 'country' AND gl.is_mappable = TRUE
  AND gl.geo_key = converted_rows.country_key
UNION ALL
SELECT gl.level, gl.geo_key, gl.parent_geo_key, gl.display_name,
  gl.country, gl.province, gl.city, gl.latitude, gl.longitude,
  converted_rows.target_class, converted_rows.category, converted_rows.source_subcategory,
  converted_rows.source_biomarker_key, converted_rows.source_biomarker_label,
  converted_rows.biomarker_cas, converted_rows.source_year,
  converted_rows.site_identity_key, converted_rows.source_city, converted_rows.doi,
  converted_rows.measurement_id, converted_rows.pndl_mg_d_1000inh, converted_rows.pndl_source
FROM converted_rows
JOIN geo_locations gl ON gl.level = 'admin1' AND gl.is_mappable = TRUE
  AND gl.parent_geo_key = converted_rows.country_key
  AND converted_rows.province_key <> ''
  AND (
    converted_rows.province_key = LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.province), ''), '[^0-9a-zA-Z]+', ''))
    OR converted_rows.province_key = LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.display_name), ''), '[^0-9a-zA-Z]+', ''))
    OR converted_rows.province_key = SUBSTRING_INDEX(gl.geo_key, '|', -1)
    OR converted_rows.province_key = CONCAT(SUBSTRING_INDEX(gl.geo_key, '|', -1), 'province')
    OR CONCAT(converted_rows.province_key, 'province') =
      LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.display_name), ''), '[^0-9a-zA-Z]+', ''))
  )
UNION ALL
SELECT gl.level, gl.geo_key, gl.parent_geo_key, gl.display_name,
  gl.country, gl.province, gl.city, gl.latitude, gl.longitude,
  converted_rows.target_class, converted_rows.category, converted_rows.source_subcategory,
  converted_rows.source_biomarker_key, converted_rows.source_biomarker_label,
  converted_rows.biomarker_cas, converted_rows.source_year,
  converted_rows.site_identity_key, converted_rows.source_city, converted_rows.doi,
  converted_rows.measurement_id, converted_rows.pndl_mg_d_1000inh, converted_rows.pndl_source
FROM converted_rows
JOIN geo_locations gl ON gl.level = 'city' AND gl.is_mappable = TRUE
  AND SUBSTRING_INDEX(gl.geo_key, '|', 1) = converted_rows.country_key
  AND converted_rows.city_key <> ''
  AND (
    converted_rows.city_key = LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.city), ''), '[^0-9a-zA-Z]+', ''))
    OR converted_rows.city_key = LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.display_name), ''), '[^0-9a-zA-Z]+', ''))
    OR converted_rows.city_key = SUBSTRING_INDEX(gl.geo_key, '|', -1)
    OR converted_rows.city_key = CONCAT(SUBSTRING_INDEX(gl.geo_key, '|', -1), 'city')
    OR CONCAT(converted_rows.city_key, 'city') =
      LOWER(REGEXP_REPLACE(COALESCE(TRIM(gl.display_name), ''), '[^0-9a-zA-Z]+', ''))
  );

ALTER TABLE map_refresh_matched
  ADD INDEX idx_refresh_region_measurement (level, geo_key, measurement_id),
  ADD INDEX idx_refresh_site (site_identity_key);

CREATE TEMPORARY TABLE map_refresh_variants (
  target_mode TINYINT NOT NULL,
  category_mode TINYINT NOT NULL,
  subcategory_mode TINYINT NOT NULL,
  biomarker_mode TINYINT NOT NULL,
  year_mode TINYINT NOT NULL
) ENGINE=MEMORY;

INSERT INTO map_refresh_variants VALUES
  (0, 0, 0, 0, 0),
  (0, 0, 1, 0, 0),
  (0, 0, 0, 1, 0),
  (0, 0, 1, 1, 0),
  (0, 0, 0, 0, 1),
  (0, 0, 1, 0, 1),
  (0, 0, 0, 1, 1),
  (0, 0, 1, 1, 1),
  (0, 1, 1, 1, 0),
  (0, 1, 1, 1, 1),
  (1, 1, 1, 1, 0),
  (1, 1, 1, 1, 1);

CREATE TEMPORARY TABLE map_refresh_expanded ENGINE=InnoDB AS
SELECT expanded.*,
  UNHEX(SHA2(CONCAT_WS(CHAR(31), expanded.level, expanded.geo_key,
    expanded.agg_target_class, expanded.agg_category, expanded.subcategory,
    expanded.biomarker_key, expanded.biomarker_label, expanded.year_label), 256)) AS group_key
FROM (
  SELECT matched.*,
    CASE WHEN variants.target_mode = 1 THEN 'ALL' ELSE matched.target_class END AS agg_target_class,
    CASE WHEN variants.category_mode = 1 THEN '全部目标物质类别' ELSE matched.category END AS agg_category,
    CASE WHEN variants.subcategory_mode = 1 THEN '全部小类' ELSE matched.source_subcategory END AS subcategory,
    CASE WHEN variants.biomarker_mode = 1 THEN 'ALL' ELSE matched.source_biomarker_key END AS biomarker_key,
    CASE WHEN variants.biomarker_mode = 1 THEN '全部 biomarker' ELSE matched.source_biomarker_label END AS biomarker_label,
    CASE WHEN variants.biomarker_mode = 1 THEN NULL ELSE matched.biomarker_cas END AS expanded_biomarker_cas,
    CASE WHEN variants.year_mode = 1 THEN '全部年份' ELSE matched.source_year END AS year_label
  FROM map_refresh_matched matched
  CROSS JOIN map_refresh_variants variants
) expanded;

ALTER TABLE map_refresh_expanded
  ADD INDEX idx_refresh_expanded_group (group_key, measurement_id),
  ADD INDEX idx_refresh_expanded_site (site_identity_key);

CREATE TEMPORARY TABLE map_refresh_points ENGINE=InnoDB AS
SELECT level, geo_key, agg_target_class, agg_category, subcategory,
  biomarker_key, biomarker_label, year_label, group_key,
  COUNT(DISTINCT site_identity_key) AS point_count,
  COUNT(DISTINCT NULLIF(TRIM(source_city), '')) AS city_count,
  COUNT(DISTINCT CASE WHEN biomarker_key <> 'ALL' AND pndl_mg_d_1000inh > 0
    THEN site_identity_key END) AS pndl_point_count
FROM map_refresh_expanded
GROUP BY level, geo_key, agg_target_class, agg_category, subcategory,
  biomarker_key, biomarker_label, year_label, group_key;

ALTER TABLE map_refresh_points
  ADD INDEX idx_refresh_points_group (group_key);

-- One observation per measurement and region prevents multi-site links from duplicating metrics.
CREATE TEMPORARY TABLE map_refresh_observations ENGINE=InnoDB AS
SELECT level, geo_key,
  MIN(parent_geo_key) AS parent_geo_key,
  MIN(display_name) AS display_name,
  MIN(country) AS country,
  MIN(province) AS province,
  MIN(city) AS city,
  MIN(latitude) AS latitude,
  MIN(longitude) AS longitude,
  agg_target_class, agg_category, subcategory, biomarker_key, biomarker_label,
  MIN(expanded_biomarker_cas) AS expanded_biomarker_cas,
  year_label, group_key, measurement_id,
  MIN(source_year) AS source_year,
  MIN(NULLIF(TRIM(doi), '')) AS doi,
  MIN(source_biomarker_key) AS source_biomarker_key,
  MAX(pndl_mg_d_1000inh) AS pndl_mg_d_1000inh,
  MAX(pndl_source) AS pndl_source
FROM map_refresh_expanded
GROUP BY level, geo_key, agg_target_class, agg_category, subcategory,
  biomarker_key, biomarker_label, year_label, group_key, measurement_id;

ALTER TABLE map_refresh_observations
  ADD INDEX idx_refresh_observation_group (group_key, measurement_id);

DELETE FROM map_pndl_stats;

INSERT INTO map_pndl_stats (
  level, geo_key, parent_geo_key, display_name, country, province, city,
  latitude, longitude, target_class, category, subcategory, biomarker_key,
  biomarker_label, biomarker_cas, year_label, pndl_median_mg_d_1000inh,
  pndl_geomean_mg_d_1000inh, pndl_mean_mg_d_1000inh,
  pndl_min_mg_d_1000inh, pndl_max_mg_d_1000inh,
  record_count, doi_count, year_count, city_count, point_count, biomarker_count,
  pndl_record_count, pndl_doi_count, pndl_point_count, pndl_year_count,
  pndl_sources, is_mappable, refreshed_at
)
WITH ranked AS (
  SELECT map_refresh_observations.*,
    ROW_NUMBER() OVER (
      PARTITION BY level, geo_key, agg_target_class, agg_category, subcategory,
        biomarker_key, biomarker_label, year_label
      ORDER BY CASE WHEN biomarker_key <> 'ALL' AND pndl_mg_d_1000inh > 0 THEN 0 ELSE 1 END,
        pndl_mg_d_1000inh, measurement_id
    ) AS pndl_rank,
    COUNT(CASE WHEN biomarker_key <> 'ALL' AND pndl_mg_d_1000inh > 0 THEN 1 END) OVER (
      PARTITION BY level, geo_key, agg_target_class, agg_category, subcategory,
        biomarker_key, biomarker_label, year_label
    ) AS pndl_value_count
  FROM map_refresh_observations
)
SELECT r.level, r.geo_key, MIN(r.parent_geo_key), MIN(r.display_name),
  MIN(r.country), MIN(r.province), MIN(r.city), MIN(r.latitude), MIN(r.longitude),
  r.agg_target_class, r.agg_category, r.subcategory, r.biomarker_key,
  r.biomarker_label,
  CASE WHEN r.biomarker_key = 'ALL' THEN NULL ELSE MIN(r.expanded_biomarker_cas) END,
  r.year_label,
  AVG(CASE WHEN r.biomarker_key <> 'ALL' AND r.pndl_mg_d_1000inh > 0
    AND r.pndl_rank IN (FLOOR((r.pndl_value_count + 1) / 2), FLOOR((r.pndl_value_count + 2) / 2))
    THEN r.pndl_mg_d_1000inh END),
  CASE WHEN r.biomarker_key = 'ALL' THEN NULL
    ELSE EXP(AVG(CASE WHEN r.pndl_mg_d_1000inh > 0 THEN LN(r.pndl_mg_d_1000inh) END)) END,
  CASE WHEN r.biomarker_key = 'ALL' THEN NULL
    ELSE AVG(CASE WHEN r.pndl_mg_d_1000inh > 0 THEN r.pndl_mg_d_1000inh END) END,
  CASE WHEN r.biomarker_key = 'ALL' THEN NULL
    ELSE MIN(CASE WHEN r.pndl_mg_d_1000inh > 0 THEN r.pndl_mg_d_1000inh END) END,
  CASE WHEN r.biomarker_key = 'ALL' THEN NULL
    ELSE MAX(CASE WHEN r.pndl_mg_d_1000inh > 0 THEN r.pndl_mg_d_1000inh END) END,
  COUNT(DISTINCT r.measurement_id),
  COUNT(DISTINCT r.doi),
  COUNT(DISTINCT CASE WHEN r.source_year <> '未标注年份' THEN r.source_year END),
  MAX(p.city_count), MAX(p.point_count), COUNT(DISTINCT r.source_biomarker_key),
  COUNT(DISTINCT CASE WHEN r.biomarker_key <> 'ALL' AND r.pndl_mg_d_1000inh > 0
    THEN r.measurement_id END),
  COUNT(DISTINCT CASE WHEN r.biomarker_key <> 'ALL' AND r.pndl_mg_d_1000inh > 0
    THEN r.doi END),
  MAX(p.pndl_point_count),
  COUNT(DISTINCT CASE WHEN r.biomarker_key <> 'ALL' AND r.pndl_mg_d_1000inh > 0
    AND r.source_year <> '未标注年份' THEN r.source_year END),
  GROUP_CONCAT(DISTINCT r.pndl_source ORDER BY r.pndl_source SEPARATOR '、'),
  TRUE, NOW()
FROM ranked r
JOIN map_refresh_points p
  ON p.group_key = r.group_key
  AND p.level = r.level AND p.geo_key = r.geo_key
  AND p.agg_target_class = r.agg_target_class
  AND p.agg_category = r.agg_category
  AND p.subcategory = r.subcategory
  AND p.biomarker_key = r.biomarker_key
  AND p.biomarker_label = r.biomarker_label
  AND p.year_label = r.year_label
GROUP BY r.level, r.geo_key, r.agg_target_class, r.agg_category, r.subcategory,
  r.biomarker_key, r.biomarker_label, r.year_label;

DROP TEMPORARY TABLE IF EXISTS map_refresh_observations;
DROP TEMPORARY TABLE IF EXISTS map_refresh_points;
DROP TEMPORARY TABLE IF EXISTS map_refresh_expanded;
DROP TEMPORARY TABLE IF EXISTS map_refresh_variants;
DROP TEMPORARY TABLE IF EXISTS map_refresh_matched;
