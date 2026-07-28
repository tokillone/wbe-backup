package com.licong.webbackup.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CoreMarkerPriorityDataContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void overviewAndLazyDetailsHaveAConsistentOneToOneContract() throws Exception {
        JsonNode overview = readJson("data/core-marker-priority-overview.json");
        JsonNode details = readJson("data/core-marker-priority-details.json");
        JsonNode rows = overview.path("rows");

        assertThat(rows.size()).isEqualTo(overview.path("summary").path("rowCount").asInt());
        Set<String> ids = new HashSet<>();
        rows.forEach(row -> {
            String id = row.path("id").asText();
            assertThat(ids.add(id)).as("marker id must be unique: %s", id).isTrue();
            assertThat(row.path("targetFine").asText()).isNotBlank();
            JsonNode scores = row.path("scoreByScope");
            assertThat(scores.has("category")).isTrue();
            assertThat(scores.has("targetType")).isTrue();
            assertThat(scores.has("targetSubtype")).isTrue();
            assertThat(scores.has("targetFine")).isTrue();
            scores.forEach(score -> {
                if (!score.path("available").asBoolean()) {
                    assertThat(score.path("totalScore").isNull()).isTrue();
                    return;
                }
                int componentTotal = score.path("literatureScore").asInt()
                        + score.path("spaceScore").asInt()
                        + score.path("yearScore").asInt()
                        + row.path("cfScore").asInt()
                        + row.path("gsScore").asInt();
                assertThat(score.path("totalScore").asInt())
                        .as("scope total must retain the five-component contract for id %s", id)
                        .isEqualTo(componentTotal);
            });
        });

        Set<String> detailIds = new HashSet<>();
        details.fieldNames().forEachRemaining(detailIds::add);
        assertThat(detailIds).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void compressedOverviewStaysWithinTheLaunchPayloadBudget() throws Exception {
        byte[] overview = readBytes("data/core-marker-priority-overview.json");

        assertThat(overview.length).isLessThan(1_900_000);
        assertThat(gzip(overview).length).isLessThan(150 * 1024);
    }

    private JsonNode readJson(String path) throws Exception {
        return objectMapper.readTree(readBytes(path));
    }

    private byte[] readBytes(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return input.readAllBytes();
        }
    }

    private byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(input);
        }
        return output.toByteArray();
    }
}
