package com.cloudspring.intelligence_rag_api.dto;

import lombok.Builder;

@Builder
public record SourceInfo(
        String documentName,
        String versionId,
        Integer chunkIndex,
        Float score
) {
}
