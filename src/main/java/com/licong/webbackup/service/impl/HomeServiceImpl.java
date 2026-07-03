package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.BiomarkerFrequencyResponse;
import com.licong.webbackup.dto.HomeOverviewResponse;
import com.licong.webbackup.dto.TargetCategoryOptionResponse;
import com.licong.webbackup.mapper.HomeMapper;
import com.licong.webbackup.service.HomeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HomeServiceImpl implements HomeService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 20;
    private static final int DEFAULT_MIN_FREQUENCY = 1;
    private static final String DEFAULT_TARGET_GROUP = "all";
    private static final String DEFAULT_TARGET_CATEGORY = "all";

    private final HomeMapper homeMapper;

    public HomeServiceImpl(HomeMapper homeMapper) {
        this.homeMapper = homeMapper;
    }

    @Override
    public HomeOverviewResponse getOverview(Integer limit, Integer minFrequency, String targetGroup, String targetCategory) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedMinFrequency = normalizeMinFrequency(minFrequency);
        String normalizedTargetGroup = normalizeTargetGroup(targetGroup);
        String normalizedTargetCategory = normalizeTargetCategory(targetCategory);
        List<BiomarkerFrequencyResponse> biomarkers =
                homeMapper.findTopBiomarkerFrequencies(normalizedTargetGroup, normalizedTargetCategory,
                        normalizedLimit, normalizedMinFrequency);
        biomarkers.forEach(biomarker -> {
            biomarker.setSubclassOptions(homeMapper.findCategorySubclasses(normalizedTargetGroup,
                    normalizedTargetCategory, biomarker.getName()));
            biomarker.setTrend(homeMapper.findCategoryBiomarkers(normalizedTargetGroup,
                    normalizedTargetCategory, biomarker.getName()));
        });
        return HomeOverviewResponse.builder()
                .biomarkerFrequencies(biomarkers)
                .targetCategoryOptions(buildTargetCategoryOptions())
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

    private String normalizeTargetCategory(String targetCategory) {
        if (targetCategory == null || targetCategory.isBlank()) {
            return DEFAULT_TARGET_CATEGORY;
        }
        String normalized = targetCategory.trim();
        if (DEFAULT_TARGET_CATEGORY.equalsIgnoreCase(normalized) || "全部".equals(normalized)) {
            return DEFAULT_TARGET_CATEGORY;
        }
        return normalized;
    }

    private List<TargetCategoryOptionResponse> buildTargetCategoryOptions() {
        List<TargetCategoryOptionResponse> options = new ArrayList<>();
        TargetCategoryOptionResponse allOption = new TargetCategoryOptionResponse();
        allOption.setValue(DEFAULT_TARGET_CATEGORY);
        allOption.setName("全部");
        allOption.setTargetGroup(DEFAULT_TARGET_GROUP);
        options.add(allOption);
        options.addAll(homeMapper.findTargetCategoryOptions());
        return options;
    }
}
