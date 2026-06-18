package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.BiomarkerFrequencyResponse;
import com.licong.webbackup.dto.HomeOverviewResponse;
import com.licong.webbackup.mapper.HomeMapper;
import com.licong.webbackup.service.HomeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HomeServiceImpl implements HomeService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 20;
    private static final int DEFAULT_MIN_FREQUENCY = 1;
    private static final String DEFAULT_TARGET_GROUP = "all";

    private final HomeMapper homeMapper;

    public HomeServiceImpl(HomeMapper homeMapper) {
        this.homeMapper = homeMapper;
    }

    @Override
    public HomeOverviewResponse getOverview(Integer limit, Integer minFrequency, String targetGroup) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedMinFrequency = normalizeMinFrequency(minFrequency);
        String normalizedTargetGroup = normalizeTargetGroup(targetGroup);
        List<BiomarkerFrequencyResponse> biomarkers =
                homeMapper.findTopBiomarkerFrequencies(normalizedTargetGroup, normalizedLimit, normalizedMinFrequency);
        biomarkers.forEach(biomarker -> {
            biomarker.setSubclassOptions(homeMapper.findCategorySubclasses(normalizedTargetGroup, biomarker.getName()));
            biomarker.setTrend(homeMapper.findCategoryBiomarkers(normalizedTargetGroup, biomarker.getName()));
        });
        return HomeOverviewResponse.builder()
                .biomarkerFrequencies(biomarkers)
                .build();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeMinFrequency(Integer minFrequency) {
        if (minFrequency == null) {
            return DEFAULT_MIN_FREQUENCY;
        }
        return Math.max(minFrequency, DEFAULT_MIN_FREQUENCY);
    }

    private String normalizeTargetGroup(String targetGroup) {
        if (targetGroup == null || targetGroup.isBlank()) {
            return DEFAULT_TARGET_GROUP;
        }
        String normalized = targetGroup.trim().toLowerCase();
        if ("drug".equals(normalized) || "consumer".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_TARGET_GROUP;
    }
}
