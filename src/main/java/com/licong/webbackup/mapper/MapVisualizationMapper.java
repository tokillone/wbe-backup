package com.licong.webbackup.mapper;

import com.licong.webbackup.dto.map.MapBreakdownRow;
import com.licong.webbackup.dto.map.MapBiomarkerPropertyRow;
import com.licong.webbackup.dto.map.MapClusterLocationRequest;
import com.licong.webbackup.dto.map.MapFilterRow;
import com.licong.webbackup.dto.map.MapRegionStatResponse;
import com.licong.webbackup.dto.map.MapMetricObservationRow;
import com.licong.webbackup.dto.map.MapSourceRecordResponse;
import com.licong.webbackup.dto.map.MapTopBiomarkerResponse;
import com.licong.webbackup.dto.map.MapTrendPointResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MapVisualizationMapper {

    @Select("""
            SELECT
              target_class,
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label
            FROM map_pndl_stats
            WHERE is_mappable = TRUE
              AND (
                pndl_median_mg_d_1000inh IS NOT NULL
                OR COALESCE(record_count, 0) > 0
                OR COALESCE(doi_count, 0) > 0
                OR COALESCE(point_count, 0) > 0
              )
            GROUP BY
              target_class,
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label
            ORDER BY
              MAX(CASE WHEN level = 'city' THEN 1 ELSE 0 END) DESC,
              MAX(CASE WHEN level = 'city' THEN city_count ELSE 0 END) DESC,
              SUM(CASE WHEN level = 'city' THEN record_count ELSE 0 END) DESC,
              target_class ASC,
              category ASC,
              CASE WHEN subcategory = '全部小类' THEN 0 ELSE 1 END,
              subcategory ASC,
              CASE WHEN biomarker_key = 'ALL' THEN 0 ELSE 1 END,
              biomarker_label ASC,
              CASE WHEN year_label = '全部年份' THEN 0 ELSE 1 END,
              year_label ASC
            """)
    List<MapFilterRow> findFilterRows();

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE category = #{category}
              AND (
                (#{category} = '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
                OR (#{category} != '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
              )
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND (
                pndl_median_mg_d_1000inh IS NOT NULL
                OR COALESCE(record_count, 0) > 0
                OR COALESCE(doi_count, 0) > 0
                OR COALESCE(point_count, 0) > 0
              )
              <if test="levels != null and levels.size() &gt; 0">
                AND level IN
                <foreach item="level" collection="levels" open="(" separator="," close=")">
                  #{level}
                </foreach>
              </if>
            ORDER BY
              FIELD(level, 'country', 'admin1', 'city'),
              pndl_median_mg_d_1000inh DESC,
              display_name ASC
            </script>
            """)
    List<MapRegionStatResponse> findStats(@Param("category") String category,
                                           @Param("targetClass") String targetClass,
                                           @Param("subcategory") String subcategory,
                                           @Param("biomarkerKey") String biomarkerKey,
                                           @Param("year") String year,
                                           @Param("levels") List<String> levels);

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE (
                (#{category} = '全部目标物质类别'
                  AND category IS NOT NULL
                  AND TRIM(category) != '')
                OR (#{category} != '全部目标物质类别' AND category = #{category})
              )
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (
                (#{subcategory} = '全部小类'
                  AND subcategory IS NOT NULL
                  AND TRIM(subcategory) != '')
                OR (#{subcategory} != '全部小类' AND subcategory = #{subcategory})
              )
              AND (
                (#{biomarkerKey} = 'ALL'
                  AND biomarker_key IS NOT NULL
                  AND TRIM(biomarker_key) != '')
                OR (#{biomarkerKey} != 'ALL' AND biomarker_key = #{biomarkerKey})
              )
              AND (
                (#{year} = '全部年份'
                  AND year_label IS NOT NULL
                  AND TRIM(year_label) != '')
                OR (#{year} != '全部年份' AND year_label = #{year})
              )
              AND is_mappable = TRUE
              AND (
                pndl_median_mg_d_1000inh IS NOT NULL
                OR COALESCE(record_count, 0) > 0
                OR COALESCE(doi_count, 0) > 0
                OR COALESCE(point_count, 0) > 0
              )
              <if test="levels != null and levels.size() &gt; 0">
                AND level IN
                <foreach item="level" collection="levels" open="(" separator="," close=")">
                  #{level}
                </foreach>
              </if>
            ORDER BY
              FIELD(level, 'country', 'admin1', 'city'),
              pndl_median_mg_d_1000inh DESC,
              display_name ASC
            </script>
            """)
    List<MapRegionStatResponse> findStatsForAnyCategory(@Param("category") String category,
                                                         @Param("targetClass") String targetClass,
                                                         @Param("subcategory") String subcategory,
                                                         @Param("biomarkerKey") String biomarkerKey,
                                                         @Param("year") String year,
                                                         @Param("levels") List<String> levels);

    @Select("""
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE level = #{level}
              AND geo_key = #{geoKey}
              AND category = #{category}
              AND (
                (#{category} = '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
                OR (#{category} != '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
              )
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
            LIMIT 1
            """)
    MapRegionStatResponse findRegion(@Param("level") String level,
                                      @Param("geoKey") String geoKey,
                                      @Param("category") String category,
                                      @Param("targetClass") String targetClass,
                                      @Param("subcategory") String subcategory,
                                      @Param("biomarkerKey") String biomarkerKey,
                                      @Param("year") String year);

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE category = #{category}
              AND (
                (#{category} = '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
                OR (#{category} != '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
              )
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND (
                pndl_median_mg_d_1000inh IS NOT NULL
                OR COALESCE(record_count, 0) > 0
                OR COALESCE(doi_count, 0) > 0
                OR COALESCE(point_count, 0) > 0
              )
              <if test="locations != null and locations.size() &gt; 0">
                AND (
                <foreach item="location" collection="locations" separator=" OR ">
                  (level = #{location.level} AND geo_key = #{location.geoKey})
                </foreach>
                )
              </if>
            ORDER BY
              FIELD(level, 'country', 'admin1', 'city'),
              pndl_median_mg_d_1000inh DESC,
              display_name ASC
            </script>
            """)
    List<MapRegionStatResponse> findRegionsByKeys(@Param("category") String category,
                                                   @Param("targetClass") String targetClass,
                                                   @Param("subcategory") String subcategory,
                                                   @Param("biomarkerKey") String biomarkerKey,
                                                   @Param("year") String year,
                                                   @Param("locations") List<MapClusterLocationRequest> locations);

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE (
                (#{category} = '全部目标物质类别'
                  AND category IS NOT NULL
                  AND TRIM(category) != '')
                OR (#{category} != '全部目标物质类别' AND category = #{category})
              )
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (
                (#{subcategory} = '全部小类'
                  AND subcategory IS NOT NULL
                  AND TRIM(subcategory) != '')
                OR (#{subcategory} != '全部小类' AND subcategory = #{subcategory})
              )
              AND (
                (#{biomarkerKey} = 'ALL'
                  AND biomarker_key IS NOT NULL
                  AND TRIM(biomarker_key) != '')
                OR (#{biomarkerKey} != 'ALL' AND biomarker_key = #{biomarkerKey})
              )
              AND (
                (#{year} = '全部年份'
                  AND year_label IS NOT NULL
                  AND TRIM(year_label) != '')
                OR (#{year} != '全部年份' AND year_label = #{year})
              )
              AND is_mappable = TRUE
              AND (
                pndl_median_mg_d_1000inh IS NOT NULL
                OR COALESCE(record_count, 0) > 0
                OR COALESCE(doi_count, 0) > 0
                OR COALESCE(point_count, 0) > 0
              )
              <if test="locations != null and locations.size() &gt; 0">
                AND (
                <foreach item="location" collection="locations" separator=" OR ">
                  (level = #{location.level} AND geo_key = #{location.geoKey})
                </foreach>
                )
              </if>
            ORDER BY
              FIELD(level, 'country', 'admin1', 'city'),
              pndl_median_mg_d_1000inh DESC,
              display_name ASC
            </script>
            """)
    List<MapRegionStatResponse> findRegionsByKeysForAnyCategory(@Param("category") String category,
                                                                 @Param("targetClass") String targetClass,
                                                                 @Param("subcategory") String subcategory,
                                                                 @Param("biomarkerKey") String biomarkerKey,
                                                                 @Param("year") String year,
                                                                 @Param("locations") List<MapClusterLocationRequest> locations);

    @Select("""
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE level = #{level}
              AND category = #{category}
              AND (
                (#{category} = '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
                OR (#{category} != '全部目标物质类别' AND (#{targetClass} = 'ALL' OR target_class = #{targetClass}))
              )
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh IS NOT NULL
            ORDER BY pndl_median_mg_d_1000inh DESC, record_count DESC, display_name ASC
            LIMIT #{limit}
            """)
    List<MapRegionStatResponse> findRankingStats(@Param("level") String level,
                                                  @Param("category") String category,
                                                  @Param("targetClass") String targetClass,
                                                  @Param("subcategory") String subcategory,
                                                  @Param("biomarkerKey") String biomarkerKey,
                                                  @Param("year") String year,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM map_pndl_stats
            WHERE level = #{level}
              AND category = #{category}
              AND (#{targetClass} = 'ALL' OR target_class = #{targetClass})
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh > #{value}
            """)
    int countHigherPndlStats(@Param("level") String level,
                             @Param("category") String category,
                             @Param("targetClass") String targetClass,
                             @Param("subcategory") String subcategory,
                             @Param("biomarkerKey") String biomarkerKey,
                             @Param("year") String year,
                             @Param("value") java.math.BigDecimal value);

    @Select("""
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE level = #{level}
              AND (
                (#{category} = '全部目标物质类别'
                  AND category IS NOT NULL
                  AND TRIM(category) != '')
                OR (#{category} != '全部目标物质类别' AND category = #{category})
              )
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (
                (#{subcategory} = '全部小类'
                  AND subcategory IS NOT NULL
                  AND TRIM(subcategory) != '')
                OR (#{subcategory} != '全部小类' AND subcategory = #{subcategory})
              )
              AND (
                (#{biomarkerKey} = 'ALL'
                  AND biomarker_key IS NOT NULL
                  AND TRIM(biomarker_key) != '')
                OR (#{biomarkerKey} != 'ALL' AND biomarker_key = #{biomarkerKey})
              )
              AND (
                (#{year} = '全部年份'
                  AND year_label IS NOT NULL
                  AND TRIM(year_label) != '')
                OR (#{year} != '全部年份' AND year_label = #{year})
              )
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh IS NOT NULL
            ORDER BY pndl_median_mg_d_1000inh DESC, record_count DESC, display_name ASC
            LIMIT #{limit}
            """)
    List<MapRegionStatResponse> findRankingStatsForAnyCategory(@Param("level") String level,
                                                                @Param("category") String category,
                                                                @Param("targetClass") String targetClass,
                                                                @Param("subcategory") String subcategory,
                                                                @Param("biomarkerKey") String biomarkerKey,
                                                                @Param("year") String year,
                                                                @Param("limit") int limit);

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE level = #{level}
              AND category = #{category}
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh IS NOT NULL
              <if test="geoKeyPrefix != null and geoKeyPrefix != ''">
                AND geo_key LIKE CONCAT(#{geoKeyPrefix}, '%')
              </if>
            ORDER BY pndl_median_mg_d_1000inh DESC, record_count DESC, display_name ASC
            LIMIT #{limit}
            </script>
            """)
    List<MapRegionStatResponse> findComparisonStats(@Param("level") String level,
                                                     @Param("geoKeyPrefix") String geoKeyPrefix,
                                                     @Param("category") String category,
                                                     @Param("targetClass") String targetClass,
                                                     @Param("subcategory") String subcategory,
                                                     @Param("biomarkerKey") String biomarkerKey,
                                                     @Param("year") String year,
                                                     @Param("limit") int limit);

    @Select("""
            <script>
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
              category,
              subcategory,
              biomarker_key,
              biomarker_label,
              biomarker_cas,
              year_label,
              pndl_median_mg_d_1000inh,
              pndl_mean_mg_d_1000inh,
              pndl_min_mg_d_1000inh,
              pndl_max_mg_d_1000inh,
              record_count,
              doi_count,
              year_count,
              city_count,
              point_count,
              biomarker_count,
              pndl_record_count,
              pndl_doi_count,
              pndl_point_count,
              pndl_year_count,
              pndl_sources
            FROM map_pndl_stats
            WHERE level = #{level}
              AND category = #{category}
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND subcategory = #{subcategory}
              AND biomarker_key = #{biomarkerKey}
              AND year_label = #{year}
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh IS NOT NULL
              <if test="countryKey != null and countryKey != ''">
                AND (geo_key = #{countryKey} OR geo_key LIKE CONCAT(#{countryKey}, '|%'))
              </if>
              <if test="parentGeoKey != null and parentGeoKey != ''">
                AND parent_geo_key = #{parentGeoKey}
              </if>
            ORDER BY pndl_median_mg_d_1000inh DESC, record_count DESC, display_name ASC
            LIMIT #{limit}
            </script>
            """)
    List<MapRegionStatResponse> findComparisonStatsByScope(@Param("level") String level,
                                                            @Param("countryKey") String countryKey,
                                                            @Param("parentGeoKey") String parentGeoKey,
                                                            @Param("category") String category,
                                                            @Param("targetClass") String targetClass,
                                                            @Param("subcategory") String subcategory,
                                                            @Param("biomarkerKey") String biomarkerKey,
                                                            @Param("year") String year,
                                                            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT CASE
              WHEN MIN(CAST(year_label AS UNSIGNED)) = MAX(CAST(year_label AS UNSIGNED))
                THEN CAST(MIN(CAST(year_label AS UNSIGNED)) AS CHAR)
              ELSE CONCAT(
                MIN(CAST(year_label AS UNSIGNED)),
                '-',
                MAX(CAST(year_label AS UNSIGNED))
              )
            END
            FROM map_pndl_stats
            WHERE level = #{level}
              AND geo_key = #{geoKey}
              AND year_label REGEXP '^[0-9]{4}$'
              AND CAST(year_label AS UNSIGNED) BETWEEN 1900 AND 2100
              AND (
                (#{category} = '全部目标物质类别'
                  AND category IS NOT NULL
                  AND TRIM(category) != '')
                OR (#{category} != '全部目标物质类别' AND category = #{category})
              )
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (
                (#{subcategory} = '全部小类'
                  AND subcategory IS NOT NULL
                  AND TRIM(subcategory) != '')
                OR (#{subcategory} != '全部小类' AND subcategory = #{subcategory})
              )
              AND (
                (#{biomarkerKey} = 'ALL'
                  AND biomarker_key IS NOT NULL
                  AND TRIM(biomarker_key) != '')
                OR (#{biomarkerKey} != 'ALL' AND biomarker_key = #{biomarkerKey})
              )
              AND is_mappable = TRUE
              AND COALESCE(record_count, 0) &gt; 0
            </script>
            """)
    String findCoverageYearRange(@Param("level") String level,
                                 @Param("geoKey") String geoKey,
                                 @Param("category") String category,
                                 @Param("targetClass") String targetClass,
                                 @Param("subcategory") String subcategory,
                                 @Param("biomarkerKey") String biomarkerKey);

    @Select("""
            <script>
            SELECT
              CAST(year_label AS UNSIGNED) AS year,
              MAX(pndl_median_mg_d_1000inh) AS value,
              MAX(pndl_record_count) AS record_count,
              MAX(pndl_record_count) AS value_count
            FROM map_pndl_stats
            WHERE level = #{level}
              AND geo_key = #{geoKey}
              AND year_label REGEXP '^[0-9]{4}$'
              AND (
                (#{category} = '全部目标物质类别'
                  AND category IS NOT NULL
                  AND TRIM(category) != '')
                OR (#{category} != '全部目标物质类别' AND category = #{category})
              )
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (
                (#{subcategory} = '全部小类'
                  AND subcategory IS NOT NULL
                  AND TRIM(subcategory) != '')
                OR (#{subcategory} != '全部小类' AND subcategory = #{subcategory})
              )
              AND biomarker_key = #{biomarkerKey}
              AND is_mappable = TRUE
              AND pndl_median_mg_d_1000inh IS NOT NULL
            GROUP BY CAST(year_label AS UNSIGNED)
            ORDER BY CAST(year_label AS UNSIGNED)
            </script>
            """)
    List<MapTrendPointResponse> findPndlTrend(@Param("level") String level,
                                              @Param("geoKey") String geoKey,
                                              @Param("category") String category,
                                              @Param("targetClass") String targetClass,
                                              @Param("subcategory") String subcategory,
                                              @Param("biomarkerKey") String biomarkerKey);

    @Select("""
            <script>
            WITH base AS (
              SELECT
                CAST(COALESCE(
                  CASE WHEN LEFT(TRIM(se.sampling_start_ym), 4) REGEXP '^[0-9]{4}$' THEN LEFT(TRIM(se.sampling_start_ym), 4) END,
                  CASE WHEN se.sample_collection_time IS NOT NULL THEN DATE_FORMAT(se.sample_collection_time, '%Y') END
                ) AS UNSIGNED) AS sample_year,
                m.plot_pndl_value,
                LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(m.plot_pndl_unit, ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) AS pndl_unit_key,
                m.plot_concentration_value,
                NULLIF(TRIM(m.plot_concentration_unit), '') AS concentration_unit,
                m.daily_load_dls_value,
                NULLIF(TRIM(m.daily_load_dls_unit), '') AS daily_load_unit,
                c.consumption_value,
                NULLIF(TRIM(c.consumption_unit), '') AS consumption_unit,
                c.usage_prevalence,
                c.disease_prevalence
              FROM measurements m
              JOIN compounds c ON c.compound_id = m.compound_id
              JOIN sampling_events se ON se.event_id = m.event_id
              JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
              JOIN geo_locations gl ON gl.level = #{level}
                AND gl.geo_key = #{geoKey}
                AND (
                  (#{level} = 'country' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country)))
                  OR (#{level} = 'admin1' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                    AND LOWER(TRIM(wp.province)) = LOWER(TRIM(gl.province)))
                  OR (#{level} = 'city' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                    AND LOWER(TRIM(wp.city)) = LOWER(TRIM(gl.city)))
                )
              WHERE (#{category} = '全部目标物质类别' OR TRIM(c.substance_category) = #{category})
                AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(c.target_category), ''), '未分类') = #{targetClass})
                AND (#{subcategory} = '全部小类' OR TRIM(c.substance_subclass) = #{subcategory})
                AND COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) = #{biomarkerKey}
            ), observations AS (
              SELECT 'pndl' AS metric_key, 'PNDL' AS metric_label, 'mg/day/1000 inh' AS unit,
                sample_year AS year,
                CASE
                  WHEN pndl_unit_key REGEXP '^mg/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR pndl_unit_key REGEXP '^mg/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN plot_pndl_value
                  WHEN pndl_unit_key REGEXP '^mg/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    THEN plot_pndl_value * 1000
                  WHEN pndl_unit_key REGEXP '^g/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR pndl_unit_key REGEXP '^g/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN plot_pndl_value * 1000
                  WHEN pndl_unit_key REGEXP '^g/(day|d)/(10000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    THEN plot_pndl_value * 100
                  WHEN pndl_unit_key REGEXP '^ug/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR pndl_unit_key REGEXP '^ug/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN plot_pndl_value / 1000
                  WHEN pndl_unit_key REGEXP '^ug/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    OR pndl_unit_key REGEXP '^ug/(inh|inhabitant|person|people|persons|capita)/(day|d).*$'
                    THEN plot_pndl_value
                END AS value
              FROM base
              UNION ALL
              SELECT 'concentration', '浓度', concentration_unit, sample_year, plot_concentration_value FROM base
              UNION ALL
              SELECT 'daily_load', '每日质量负荷', daily_load_unit, sample_year, daily_load_dls_value FROM base
              UNION ALL
              SELECT 'drug_consumption', '药物消费量', consumption_unit, sample_year, consumption_value FROM base
              UNION ALL
              SELECT 'usage_rate', '用药率', '%', sample_year, usage_prevalence FROM base
              UNION ALL
              SELECT 'disease_prevalence', '患病率', '%', sample_year, disease_prevalence FROM base
            )
            SELECT metric_key, metric_label, unit, year, value
            FROM observations
            WHERE year BETWEEN 1900 AND 2100
              AND value IS NOT NULL
              AND value &gt; 0
              AND unit IS NOT NULL
              AND TRIM(unit) != ''
            ORDER BY FIELD(metric_key, 'pndl', 'concentration', 'daily_load', 'drug_consumption', 'usage_rate', 'disease_prevalence'), unit, year
            </script>
            """)
    List<MapMetricObservationRow> findMetricObservations(@Param("level") String level,
                                                          @Param("geoKey") String geoKey,
                                                          @Param("category") String category,
                                                          @Param("targetClass") String targetClass,
                                                          @Param("subcategory") String subcategory,
                                                          @Param("biomarkerKey") String biomarkerKey);

    @Select("""
            <script>
            SELECT
              COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) AS biomarker_key,
              COALESCE(NULLIF(TRIM(c.biomarker_name), ''), c.drug_name) AS biomarker_label,
              c.biomarker_cas,
              COALESCE(NULLIF(TRIM(c.target_category), ''), '未分类') AS target_class,
              c.substance_category AS category,
              c.substance_subclass AS subcategory,
              NULLIF(TRIM(c.physicochemical_properties), '') AS property_text,
              COUNT(DISTINCT m.measurement_id) AS record_count
            FROM measurements m
            JOIN compounds c ON c.compound_id = m.compound_id
            JOIN sampling_events se ON se.event_id = m.event_id
            JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
            JOIN geo_locations gl ON gl.level = #{level}
              AND gl.geo_key = #{geoKey}
              AND (
                (#{level} = 'country' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country)))
                OR (#{level} = 'admin1' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                  AND LOWER(TRIM(wp.province)) = LOWER(TRIM(gl.province)))
                OR (#{level} = 'city' AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                  AND LOWER(TRIM(wp.city)) = LOWER(TRIM(gl.city)))
              )
            WHERE (#{category} = '全部目标物质类别' OR TRIM(c.substance_category) = #{category})
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(c.target_category), ''), '未分类') = #{targetClass})
              AND (#{subcategory} = '全部小类' OR TRIM(c.substance_subclass) = #{subcategory})
              AND (#{biomarkerKey} = 'ALL'
                OR COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) = #{biomarkerKey})
              AND NULLIF(TRIM(c.physicochemical_properties), '') IS NOT NULL
            GROUP BY c.compound_id, biomarker_key, biomarker_label, c.biomarker_cas,
              target_class, c.substance_category, c.substance_subclass, property_text
            ORDER BY record_count DESC, biomarker_label ASC
            LIMIT 40
            </script>
            """)
    List<MapBiomarkerPropertyRow> findBiomarkerProperties(@Param("level") String level,
                                                           @Param("geoKey") String geoKey,
                                                           @Param("category") String category,
                                                           @Param("targetClass") String targetClass,
                                                           @Param("subcategory") String subcategory,
                                                           @Param("biomarkerKey") String biomarkerKey);

    @Select("""
            <script>
            SELECT
              biomarker_key,
              COALESCE(NULLIF(TRIM(biomarker_label), ''), biomarker_key) AS biomarker_label,
              MAX(biomarker_cas) AS biomarker_cas,
              MIN(COALESCE(NULLIF(TRIM(target_class), ''), '未分类')) AS target_class,
              MIN(category) AS category,
              MIN(subcategory) AS subcategory,
              SUM(record_count) AS record_count,
              SUM(doi_count) AS doi_count,
              SUM(point_count) AS point_count,
              MAX(CASE WHEN pndl_median_mg_d_1000inh IS NOT NULL THEN 1 ELSE 0 END) AS has_pndl
            FROM map_pndl_stats
            WHERE (category IS NULL OR TRIM(category) = '' OR category != '全部目标物质类别')
              AND (#{category} = '全部目标物质类别' OR category = #{category})
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (subcategory IS NULL OR TRIM(subcategory) = '' OR subcategory != '全部小类')
              AND (#{subcategory} = '全部小类' OR subcategory = #{subcategory})
              AND biomarker_key != 'ALL'
              AND (#{biomarkerKey} = 'ALL' OR biomarker_key = #{biomarkerKey})
              AND (
                (#{year} = '全部年份'
                  AND year_label = '全部年份')
                OR (#{year} != '全部年份' AND year_label = #{year})
              )
              AND is_mappable = TRUE
              <if test="locations != null and locations.size() &gt; 0">
                AND (
                <foreach item="location" collection="locations" separator=" OR ">
                  (level = #{location.level} AND geo_key = #{location.geoKey})
                </foreach>
                )
              </if>
            GROUP BY biomarker_key, biomarker_label
            ORDER BY SUM(doi_count) DESC, SUM(record_count) DESC, SUM(point_count) DESC, biomarker_label ASC
            LIMIT #{limit}
            </script>
            """)
    List<MapTopBiomarkerResponse> findTopBiomarkersForLocations(@Param("category") String category,
                                                                 @Param("targetClass") String targetClass,
                                                                 @Param("subcategory") String subcategory,
                                                                 @Param("biomarkerKey") String biomarkerKey,
                                                                 @Param("year") String year,
                                                                 @Param("locations") List<MapClusterLocationRequest> locations,
                                                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              COALESCE(NULLIF(TRIM(category), ''), '未分类') AS label,
              SUM(record_count) AS record_count
            FROM map_pndl_stats
            WHERE (category IS NULL OR TRIM(category) = '' OR category != '全部目标物质类别')
              AND (#{category} = '全部目标物质类别' OR category = #{category})
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(target_class), ''), '未分类') = #{targetClass})
              AND (biomarker_key IS NULL OR biomarker_key != 'ALL')
              AND (
                (#{year} = '全部年份'
                  AND year_label IS NOT NULL
                  AND TRIM(year_label) != ''
                  AND year_label != '全部年份')
                OR (#{year} != '全部年份' AND year_label = #{year})
              )
              AND is_mappable = TRUE
              <if test="locations != null and locations.size() &gt; 0">
                AND (
                <foreach item="location" collection="locations" separator=" OR ">
                  (level = #{location.level} AND geo_key = #{location.geoKey})
                </foreach>
                )
              </if>
            GROUP BY COALESCE(NULLIF(TRIM(category), ''), '未分类')
            ORDER BY SUM(record_count) DESC, label ASC
            LIMIT #{limit}
            </script>
            """)
    List<MapBreakdownRow> findCategoryBreakdownForLocations(@Param("category") String category,
                                                            @Param("targetClass") String targetClass,
                                                            @Param("year") String year,
                                                            @Param("locations") List<MapClusterLocationRequest> locations,
                                                            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              m.measurement_id,
              c.drug_name,
              COALESCE(NULLIF(TRIM(c.biomarker_name), ''), c.drug_name) AS biomarker_name,
              c.biomarker_cas,
              c.doi,
              wp.country,
              wp.province,
              wp.city,
              wp.plant_name,
              COALESCE(NULLIF(TRIM(se.sampling_start_ym), ''), DATE_FORMAT(se.sample_collection_time, '%Y-%m')) AS sample_period,
              se.source_workbook,
              se.original_row_number,
              CASE
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^mg/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                  OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^mg/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                  THEN m.plot_pndl_value
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^mg/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                  THEN m.plot_pndl_value * 1000
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^g/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                  OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^g/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                  THEN m.plot_pndl_value * 1000
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^g/(day|d)/(10000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                  THEN m.plot_pndl_value * 100
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^ug/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                  OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^ug/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                  THEN m.plot_pndl_value / 1000
                WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^ug/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                  OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(
                  m.plot_pndl_unit,
                  ''), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) REGEXP '^ug/(inh|inhabitant|person|people|persons|capita)/(day|d).*$'
                  THEN m.plot_pndl_value
                ELSE NULL
              END AS pndl_mg_d_1000inh,
              CASE WHEN m.plot_pndl_value IS NOT NULL THEN '做图PNDL' END AS pndl_source,
              m.plot_concentration_value AS concentration_value,
              NULLIF(TRIM(m.plot_concentration_unit), '') AS concentration_unit,
              m.daily_load_dls_value AS daily_load_value,
              NULLIF(TRIM(m.daily_load_dls_unit), '') AS daily_load_unit
            FROM measurements m
            JOIN compounds c ON c.compound_id = m.compound_id
            JOIN sampling_events se ON se.event_id = m.event_id
            JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
            JOIN geo_locations gl ON gl.level = #{level}
              AND gl.geo_key = #{geoKey}
              AND (
                (#{level} = 'country'
                  AND (LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                    OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.country), ' ', '_'), '-', '_'), '.', '')) = gl.geo_key))
                OR (#{level} = 'admin1'
                  AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                  AND (LOWER(TRIM(wp.province)) = LOWER(TRIM(gl.province))
                    OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.province), ' ', '_'), '-', '_'), '.', '')) = SUBSTRING_INDEX(gl.geo_key, '|', -1)))
                OR (#{level} = 'city'
                  AND LOWER(TRIM(wp.country)) = LOWER(TRIM(gl.country))
                  AND (LOWER(TRIM(wp.city)) = LOWER(TRIM(gl.city))
                    OR LOWER(REPLACE(REPLACE(REPLACE(TRIM(wp.city), ' ', '_'), '-', '_'), '.', '')) = SUBSTRING_INDEX(gl.geo_key, '|', -1)))
              )
            WHERE (#{category} = '全部目标物质类别' OR TRIM(c.substance_category) = #{category})
              AND (#{targetClass} = 'ALL' OR COALESCE(NULLIF(TRIM(c.target_category), ''), '未分类') = #{targetClass})
              AND (#{subcategory} = '全部小类' OR TRIM(c.substance_subclass) = #{subcategory})
              AND (#{biomarkerKey} = 'ALL'
                OR COALESCE(NULLIF(REPLACE(TRIM(c.biomarker_cas), '-', ''), ''), CAST(c.compound_id AS CHAR)) = #{biomarkerKey})
              AND (#{year} = '全部年份'
                OR COALESCE(
                  CASE WHEN LEFT(TRIM(se.sampling_start_ym), 4) REGEXP '^[0-9]{4}$' THEN LEFT(TRIM(se.sampling_start_ym), 4) END,
                  CASE WHEN se.sample_collection_time IS NOT NULL THEN DATE_FORMAT(se.sample_collection_time, '%Y') END,
                  '未标注年份'
                ) = #{year})
            ORDER BY
              CASE WHEN pndl_mg_d_1000inh IS NULL OR pndl_mg_d_1000inh &lt;= 0 THEN 1 ELSE 0 END,
              pndl_mg_d_1000inh DESC,
              m.measurement_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<MapSourceRecordResponse> findSourceRecords(@Param("level") String level,
                                                    @Param("geoKey") String geoKey,
                                                    @Param("category") String category,
                                                    @Param("targetClass") String targetClass,
                                                    @Param("subcategory") String subcategory,
                                                    @Param("biomarkerKey") String biomarkerKey,
                                                    @Param("year") String year,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM map_pndl_stats
            WHERE is_mappable = TRUE
              AND (record_count > 0 OR doi_count > 0 OR point_count > 0)
            """)
    long countStatsRows();

    @Select("""
            SELECT COUNT(*)
            FROM geo_locations
            WHERE is_mappable = TRUE
            """)
    long countMappableGeoLocations();

    @Select("""
            SELECT COUNT(*)
            FROM measurements
            WHERE plot_pndl_value > 0
            """)
    long countPositivePndlRows();

    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT
                CASE
                  WHEN unit_key REGEXP '^mg/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^mg/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value
                  WHEN unit_key REGEXP '^mg/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    THEN raw_value * 1000
                  WHEN unit_key REGEXP '^g/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^g/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value * 1000
                  WHEN unit_key REGEXP '^g/(day|d)/(10000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    THEN raw_value * 100
                  WHEN unit_key REGEXP '^ug/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^ug/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value / 1000
                  WHEN unit_key REGEXP '^ug/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    OR unit_key REGEXP '^ug/(inh|inhabitant|person|people|persons|capita)/(day|d).*$'
                    THEN raw_value
                  ELSE NULL
                END AS pndl_mg_d_1000inh
              FROM (
                SELECT
                  m.plot_pndl_value AS raw_value,
                  LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    COALESCE(
                      m.plot_pndl_unit,
                      ''
                    ), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) AS unit_key
                FROM measurements m
                JOIN compounds c ON c.compound_id = m.compound_id
                WHERE NULLIF(TRIM(c.substance_category), '') IS NOT NULL
              ) unit_rows
            ) converted_rows
            WHERE pndl_mg_d_1000inh > 0
            """)
    long countConvertiblePndlRows();

    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT
                country_key,
                CASE
                  WHEN unit_key REGEXP '^mg/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^mg/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value
                  WHEN unit_key REGEXP '^mg/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    THEN raw_value * 1000
                  WHEN unit_key REGEXP '^g/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^g/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value * 1000
                  WHEN unit_key REGEXP '^g/(day|d)/(10000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    THEN raw_value * 100
                  WHEN unit_key REGEXP '^ug/(day|d)/(1000(inh|inhabitants|people|persons|person|capita|p|pop))$'
                    OR unit_key REGEXP '^ug/1000(inh|inhabitants|people|persons|person|capita|p|pop)/(day|d)$'
                    THEN raw_value / 1000
                  WHEN unit_key REGEXP '^ug/(day|d)/(inh|inhabitant|person|people|persons|capita).*$'
                    OR unit_key REGEXP '^ug/(inh|inhabitant|person|people|persons|capita)/(day|d).*$'
                    THEN raw_value
                  ELSE NULL
                END AS pndl_mg_d_1000inh
              FROM (
                SELECT
                  CASE
                    WHEN LOWER(REGEXP_REPLACE(COALESCE(TRIM(wp.country), ''), '[^0-9a-zA-Z]+', '')) IN ('unitedstates', 'unitedstatesofamerica', 'usa', 'us')
                      THEN 'unitedsofamerica'
                    WHEN LOWER(REGEXP_REPLACE(COALESCE(TRIM(wp.country), ''), '[^0-9a-zA-Z]+', '')) = 'czechrepublic'
                      THEN 'czechia'
                    WHEN LOWER(REGEXP_REPLACE(COALESCE(TRIM(wp.country), ''), '[^0-9a-zA-Z]+', '')) = 'vietnam'
                      THEN 'vietnam'
                    ELSE LOWER(REGEXP_REPLACE(COALESCE(TRIM(wp.country), ''), '[^0-9a-zA-Z]+', ''))
                  END AS country_key,
                  m.plot_pndl_value AS raw_value,
                  LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    COALESCE(
                      m.plot_pndl_unit,
                      ''
                    ), 'μ', 'u'), 'µ', 'u'), ' ', ''), '.', ''), '-', '')) AS unit_key
                FROM measurements m
                JOIN compounds c ON c.compound_id = m.compound_id
                JOIN sampling_events se ON se.event_id = m.event_id
                JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
                WHERE NULLIF(TRIM(c.substance_category), '') IS NOT NULL
              ) unit_rows
            ) converted_rows
            JOIN geo_locations gl ON gl.level = 'country'
              AND gl.is_mappable = TRUE
              AND gl.geo_key = converted_rows.country_key
            WHERE pndl_mg_d_1000inh > 0
            """)
    long countMappablePndlRows();
}
