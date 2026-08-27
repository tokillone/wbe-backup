package com.licong.webbackup.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CoreMarkerPriorityRepository {

    private static final String DATASET_KEY = "default";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CoreMarkerPriorityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public CoreMarkerPriorityOverviewResponse findOverview() {
        List<JsonNode> rows = jdbcTemplate.query(
                """
                        SELECT overview_json
                        FROM core_marker_priority_records
                        ORDER BY CASE WHEN overall_rank IS NULL THEN 1 ELSE 0 END,
                                 overall_rank,
                                 marker_id
                        """,
                (resultSet, rowNumber) -> readJson(resultSet.getString("overview_json"))
        );
        JsonNode summary = jdbcTemplate.query(
                """
                        SELECT summary_json
                        FROM core_marker_priority_datasets
                        WHERE dataset_key = ?
                        """,
                resultSet -> resultSet.next()
                        ? readJson(resultSet.getString("summary_json"))
                        : objectMapper.createObjectNode(),
                DATASET_KEY
        );
        return new CoreMarkerPriorityOverviewResponse(rows, summary);
    }

    public Optional<JsonNode> findDetail(String markerId) {
        List<JsonNode> results = jdbcTemplate.query(
                """
                        SELECT detail_json
                        FROM core_marker_priority_records
                        WHERE marker_id = ?
                        """,
                (resultSet, rowNumber) -> readJson(resultSet.getString("detail_json")),
                markerId
        );
        return results.stream().findFirst();
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("核心标记物数据库 JSON 数据损坏", exception);
        }
    }
}
