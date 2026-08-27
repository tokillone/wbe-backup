package com.licong.webbackup.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.repository.CoreMarkerPriorityRepository;
import com.licong.webbackup.service.CoreMarkerPriorityService;
import org.springframework.stereotype.Service;

@Service
public class CoreMarkerPriorityServiceImpl implements CoreMarkerPriorityService {

    private final CoreMarkerPriorityRepository coreMarkerPriorityRepository;

    public CoreMarkerPriorityServiceImpl(CoreMarkerPriorityRepository coreMarkerPriorityRepository) {
        this.coreMarkerPriorityRepository = coreMarkerPriorityRepository;
    }

    @Override
    public CoreMarkerPriorityOverviewResponse getOverview() {
        return coreMarkerPriorityRepository.findOverview();
    }

    @Override
    public JsonNode getDetail(String markerId) {
        return coreMarkerPriorityRepository.findDetail(markerId)
                .orElseThrow(() -> new BusinessException(404, "核心标记物不存在"));
    }
}
