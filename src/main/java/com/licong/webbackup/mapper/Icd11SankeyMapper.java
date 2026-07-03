package com.licong.webbackup.mapper;

import com.licong.webbackup.dto.sankey.Icd11SankeyPathRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface Icd11SankeyMapper {

    @Select("""
            SELECT target_category
            FROM icd11_sankey_paths
            GROUP BY target_category
            ORDER BY target_category ASC
            """)
    List<String> findCategories();

    @Select("""
            SELECT
              sankey_path_id,
              target_category,
              substance_category,
              substance_subclass,
              drug_name,
              indication_original,
              biomarker_name,
              biomarker_alias,
              normalized_indication,
              disease_entity,
              icd11_level1_code,
              icd11_level1_name,
              icd11_level2_code,
              icd11_level2_name,
              icd11_level3_code,
              icd11_level3_name,
              mapping_level,
              match_type,
              review_status,
              note,
              biomarker_cas,
              literature_count,
              data_row_count
            FROM icd11_sankey_paths
            WHERE target_category = #{category}
            ORDER BY
              literature_count DESC,
              icd11_level1_name ASC,
              icd11_level2_name ASC,
              drug_name ASC,
              biomarker_name ASC,
              sankey_path_id ASC
            """)
    List<Icd11SankeyPathRow> findPathsByCategory(@Param("category") String category);
}
