package com.licong.webbackup.mapper;

import com.licong.webbackup.dto.methodology.MethodologyOptionRow;
import com.licong.webbackup.dto.methodology.MethodologyRecordResponse;
import com.licong.webbackup.dto.methodology.MethodologySamplingMethodRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MethodologyMapper {

    @Select("SELECT source_checksum FROM methodology_dataset_meta WHERE dataset_id = 1")
    String findSourceChecksum();

    @Select("SELECT meta_json FROM methodology_dataset_meta WHERE dataset_id = 1")
    String findMetaJson();

    @Select("""
            SELECT
              doc_code AS doc,
              doi,
              target_class,
              category,
              subcategory,
              drug,
              marker,
              prescription,
              sampling_raw,
              sampling_standard,
              sampling_detail,
              sampling_class,
              sample_object,
              proportion,
              duration,
              passive_sampler,
              station_status,
              analysis_raw,
              analysis_group,
              country
            FROM methodology_records
            ORDER BY record_id
            """)
    List<MethodologyRecordResponse> findAllRecords();

    @Select("""
            SELECT
              standard,
              sampling_class_json,
              sample_object_json,
              proportion_json,
              duration_json,
              passive_sampler_json,
              station_status_json,
              audit_source_groups,
              impact_rows
            FROM methodology_sampling_methods
            ORDER BY display_order
            """)
    List<MethodologySamplingMethodRow> findSamplingMethods();

    @Select("""
            SELECT option_type, option_value
            FROM methodology_options
            ORDER BY option_type, display_order
            """)
    List<MethodologyOptionRow> findOptions();
}
