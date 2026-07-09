package com.cloudspring.intelligence_rag_api.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        List<SourceInfo> sources
) {
}