package com.licong.webbackup.mapper;

import com.licong.webbackup.dto.BiomarkerFrequencyResponse;
import com.licong.webbackup.dto.BiomarkerSubclassResponse;
import com.licong.webbackup.dto.BiomarkerTrendPointResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeMapper {

    @Select("""
	            SELECT
	              h.substance_category AS name,
	              COUNT(DISTINCT h.doi) AS frequency,
	              COUNT(DISTINCT h.doi) AS docs,
	              COUNT(*) AS `rows`,
	              MIN(h.target_category) AS category,
	              MIN(h.target_category) AS target_category,
	              MIN(h.target_group) AS target_group
            FROM home_target_records h
            WHERE (#{targetGroup} = 'all' OR h.target_group = #{targetGroup})
              AND h.substance_category IS NOT NULL
              AND h.substance_category <> ''
              AND h.doi IS NOT NULL
              AND h.doi <> ''
            GROUP BY h.substance_category
            HAVING frequency >= #{minFrequency}
            ORDER BY frequency DESC, `rows` DESC, name ASC
            LIMIT #{limit}
            """)
    List<BiomarkerFrequencyResponse> findTopBiomarkerFrequencies(@Param("targetGroup") String targetGroup,
                                                                  @Param("limit") int limit,
                                                                  @Param("minFrequency") int minFrequency);

    @Select("""
            SELECT
              h.substance_subclass AS name,
              COUNT(DISTINCT h.doi) AS frequency,
              COUNT(DISTINCT h.biomarker_name) AS biomarker_count
            FROM home_target_records h
            WHERE (#{targetGroup} = 'all' OR h.target_group = #{targetGroup})
              AND h.substance_category = #{name}
              AND h.substance_subclass IS NOT NULL
              AND h.substance_subclass <> ''
            GROUP BY h.substance_subclass
            HAVING frequency > 0
            ORDER BY frequency DESC, biomarker_count DESC, name ASC
            """)
    List<BiomarkerSubclassResponse> findCategorySubclasses(@Param("targetGroup") String targetGroup,
                                                           @Param("name") String name);

    @Select("""
	            SELECT
	              h.biomarker_name AS period,
	              h.substance_subclass AS subclass,
	              COUNT(DISTINCT h.doi) AS frequency
	            FROM home_target_records h
            WHERE (#{targetGroup} = 'all' OR h.target_group = #{targetGroup})
              AND h.substance_category = #{name}
              AND h.biomarker_name IS NOT NULL
              AND h.biomarker_name <> ''
              AND h.doi IS NOT NULL
              AND h.doi <> ''
            GROUP BY h.substance_subclass, h.biomarker_name
            HAVING period <> ''
              AND frequency > 0
            ORDER BY h.substance_subclass ASC, frequency DESC, period ASC
            """)
    List<BiomarkerTrendPointResponse> findCategoryBiomarkers(@Param("targetGroup") String targetGroup,
                                                             @Param("name") String name);
}
