package com.licong.webbackup.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.methodology.MethodologyDataResponse;
import com.licong.webbackup.dto.methodology.MethodologyOptionRow;
import com.licong.webbackup.dto.methodology.MethodologyOptionsResponse;
import com.licong.webbackup.dto.methodology.MethodologyRecordResponse;
import com.licong.webbackup.dto.methodology.MethodologySamplingMethodResponse;
import com.licong.webbackup.dto.methodology.MethodologySamplingMethodRow;
import com.licong.webbackup.mapper.MethodologyMapper;
import com.licong.webbackup.service.MethodologyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MethodologyServiceImpl implements MethodologyService {

    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private final MethodologyMapper methodologyMapper;
    private final ObjectMapper objectMapper;

    public MethodologyServiceImpl(MethodologyMapper methodologyMapper, ObjectMapper objectMapper) {
        this.methodologyMapper = methodologyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getVersion() {
        String checksum = methodologyMapper.findSourceChecksum();
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalStateException("方法学数据尚未初始化");
        }
        return checksum;
    }

    @Override
    public Map<String, Object> getOverview() {
        String metaJson = methodologyMapper.findMetaJson();
        if (metaJson == null || metaJson.isBlank()) {
            throw new IllegalStateException("方法学数据尚未初始化");
        }
        return readJson(metaJson, META_TYPE);
    }

    @Override
    public MethodologyOptionsResponse getOptions() {
        LinkedHashMap<String, List<String>> options = new LinkedHashMap<>();
        for (MethodologyOptionRow row : methodologyMapper.findOptions()) {
            options.computeIfAbsent(row.getOptionType(), ignored -> new ArrayList<>())
                    .add(row.getOptionValue());
        }

        return MethodologyOptionsResponse.builder()
                .samplingMethods(methodologyMapper.findSamplingMethods().stream()
                        .map(this::toSamplingMethod)
                        .toList())
                .options(options)
                .build();
    }

    @Override
    public List<MethodologyRecordResponse> getRecords() {
        return methodologyMapper.findAllRecords();
    }

    @Override
    public MethodologyDataResponse getData() {
        MethodologyOptionsResponse options = getOptions();
        return MethodologyDataResponse.builder()
                .meta(getOverview())
                .records(getRecords())
                .samplingMethods(options.getSamplingMethods())
                .options(options.getOptions())
                .build();
    }

    private MethodologySamplingMethodResponse toSamplingMethod(MethodologySamplingMethodRow row) {
        return MethodologySamplingMethodResponse.builder()
                .standard(row.getStandard())
                .samplingClass(readJson(row.getSamplingClassJson(), STRING_LIST_TYPE))
                .sampleObject(readJson(row.getSampleObjectJson(), STRING_LIST_TYPE))
                .proportion(readJson(row.getProportionJson(), STRING_LIST_TYPE))
                .duration(readJson(row.getDurationJson(), STRING_LIST_TYPE))
                .passiveSampler(readJson(row.getPassiveSamplerJson(), STRING_LIST_TYPE))
                .stationStatus(readJson(row.getStationStatusJson(), STRING_LIST_TYPE))
                .auditSourceGroups(row.getAuditSourceGroups())
                .impactRows(row.getImpactRows())
                .build();
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("方法学数据库内容无法解析", exception);
        }
    }
}
