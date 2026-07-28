package com.licong.webbackup.mapper;

import com.licong.webbackup.dto.BiomarkerFrequencyResponse;
import com.licong.webbackup.dto.HomeSubclassRow;
import com.licong.webbackup.dto.HomeTrendRow;
import com.licong.webbackup.dto.TargetCategoryOptionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeMapper {

    @Select("""
            <script>
            SELECT
              h.substance_category AS name,
              COUNT(DISTINCT h.doi) AS frequency,
              COUNT(DISTINCT h.doi) AS docs,
              COUNT(*) AS `rows`,
              MIN(h.target_category) AS category,
              MIN(h.target_category) AS target_category,
              MIN(h.target_group) AS target_group
            FROM home_target_records h
            WHERE h.substance_category IS NOT NULL
              AND h.substance_category &lt;&gt; ''
              AND h.doi IS NOT NULL
              AND h.doi &lt;&gt; ''
              <if test="targetCategory != 'all'">
                AND h.target_category = #{targetCategory}
              </if>
              <if test="targetCategory == 'all' and targetGroup != 'all'">
                AND h.target_group = #{targetGroup}
              </if>
            GROUP BY h.substance_category
            HAVING frequency &gt;= #{minFrequency}
            ORDER BY frequency DESC, `rows` DESC, name ASC
            LIMIT #{limit}
            </script>
            """)
    List<BiomarkerFrequencyResponse> findTopBiomarkerFrequencies(@Param("targetGroup") String targetGroup,
                                                                  @Param("targetCategory") String targetCategory,
                                                                  @Param("limit") int limit,
                                                                  @Param("minFrequency") int minFrequency);

    @Select("""
            <script>
            SELECT
              h.substance_category AS category_name,
              h.substance_subclass AS name,
              COUNT(DISTINCT h.doi) AS frequency,
              COUNT(DISTINCT h.biomarker_name) AS biomarker_count
            FROM home_target_records h
            WHERE h.substance_category IN
              <foreach collection="categoryNames" item="categoryName" open="(" separator="," close=")">
                #{categoryName}
              </foreach>
              AND h.substance_subclass IS NOT NULL
              AND h.substance_subclass &lt;&gt; ''
              <if test="targetCategory != 'all'">
                AND h.target_category = #{targetCategory}
              </if>
              <if test="targetCategory == 'all' and targetGroup != 'all'">
                AND h.target_group = #{targetGroup}
              </if>
            GROUP BY h.substance_category, h.substance_subclass
            HAVING frequency &gt; 0
            ORDER BY h.substance_category ASC, frequency DESC, biomarker_count DESC, name ASC
            </script>
            """)
    List<HomeSubclassRow> findCategorySubclasses(@Param("targetGroup") String targetGroup,
                                                 @Param("targetCategory") String targetCategory,
                                                 @Param("categoryNames") List<String> categoryNames);

    @Select("""
            <script>
            SELECT
              h.substance_category AS category_name,
              h.biomarker_name AS period,
              h.substance_subclass AS subclass,
              COUNT(DISTINCT h.doi) AS frequency
            FROM home_target_records h
            WHERE h.substance_category IN
              <foreach collection="categoryNames" item="categoryName" open="(" separator="," close=")">
                #{categoryName}
              </foreach>
              AND h.biomarker_name IS NOT NULL
              AND h.biomarker_name &lt;&gt; ''
              AND h.doi IS NOT NULL
              AND h.doi &lt;&gt; ''
              <if test="targetCategory != 'all'">
                AND h.target_category = #{targetCategory}
              </if>
              <if test="targetCategory == 'all' and targetGroup != 'all'">
                AND h.target_group = #{targetGroup}
              </if>
            GROUP BY h.substance_category, h.substance_subclass, h.biomarker_name
            HAVING period &lt;&gt; ''
              AND frequency &gt; 0
            ORDER BY h.substance_category ASC, h.substance_subclass ASC, frequency DESC, period ASC
            </script>
            """)
    List<HomeTrendRow> findCategoryBiomarkers(@Param("targetGroup") String targetGroup,
                                              @Param("targetCategory") String targetCategory,
                                              @Param("categoryNames") List<String> categoryNames);

    @Select("""
            SELECT
              h.target_category AS value,
              h.target_category AS name,
              COUNT(DISTINCT h.doi) AS frequency,
              MIN(h.target_group) AS target_group
            FROM home_target_records h
            WHERE h.target_category IS NOT NULL
              AND h.target_category <> ''
              AND h.doi IS NOT NULL
              AND h.doi <> ''
            GROUP BY h.target_category
            HAVING frequency > 0
            ORDER BY frequency DESC, COUNT(*) DESC, name ASC
            """)
    List<TargetCategoryOptionResponse> findTargetCategoryOptions();
}
