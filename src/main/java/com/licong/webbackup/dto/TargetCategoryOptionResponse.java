package com.licong.webbackup.dto;

import lombok.Data;

@Data
public class TargetCategoryOptionResponse {

    private String value;
    private String name;
    private Long frequency;
    private String targetGroup;
}
