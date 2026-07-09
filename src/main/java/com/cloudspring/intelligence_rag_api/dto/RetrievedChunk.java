package com.cloudspring.intelligence_rag_api.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RetrievedChunk {
    String documentName;
    String versionId;
    Integer chunkIndex;
    String content;
    Float score;
}