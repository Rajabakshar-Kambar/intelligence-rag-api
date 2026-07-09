package com.cloudspring.intelligence_rag_api.embedding;

import com.cloudspring.intelligence_rag_api.config.AwsProperties;
import com.cloudspring.intelligence_rag_api.exception.EmbeddingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitanEmbeddingAdapterTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private TitanEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        AwsProperties props = new AwsProperties(
                "ap-south-1",
                "amazon.titan-embed-text-v2:0",
                10,
                30
        );
        adapter = new TitanEmbeddingAdapter(bedrockClient, new ObjectMapper(), props);
    }

    @Test
    void embedText_parsesBedrockResponseCorrectly() {
        String bedrockJson = "{\"embedding\": [0.1, 0.2, 0.3]}";
        InvokeModelResponse response = InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String(bedrockJson))
                .build();
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(response);

        float[] vector = adapter.embedText("hello world");

        assertThat(vector).hasSize(3);
        assertThat(vector[0]).isCloseTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(vector[1]).isCloseTo(0.2f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(vector[2]).isCloseTo(0.3f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    void embedText_whenBedrockThrows_wrapsInEmbeddingException() {
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(new RuntimeException("network error"));

        assertThatThrownBy(() -> adapter.embedText("hello"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Titan embedding call failed");
    }

    @Test
    void dimensions_returns1024() {
        assertThat(adapter.dimensions()).isEqualTo(1024);
    }

    @Test
    void sha256_returnsDifferentHashesForDifferentInputs() {
        String hash1 = adapter.sha256("hello");
        String hash2 = adapter.sha256("world");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sha256_returnsSameHashForSameInput() {
        assertThat(adapter.sha256("deterministic")).isEqualTo(adapter.sha256("deterministic"));
    }
}