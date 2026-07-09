package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.dto.ChatResponse;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import com.cloudspring.intelligence_rag_api.exception.LlmException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagChatServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequest;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;
    @Mock
    private QdrantRetrieverService retrieverService;
    @Mock
    private PromptService promptService;

    private RagChatService ragChatService;

    @BeforeEach
    void setUp() {
        ragChatService = new RagChatService(
                chatClient,
                retrieverService,
                promptService,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void chat_whenNoChunksRetrieved_returnsNoContextResponse() {
        when(retrieverService.retrieve(anyString())).thenReturn(List.of());

        ChatResponse response = ragChatService.chat("What is the vacation policy?");

        assertThat(response.answer()).contains("could not find relevant information");
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(promptService, chatClient);
    }

    @Test
    void chat_whenChunksRetrieved_buildsPromptAndCallsLlm() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .documentName("policy.pdf")
                .versionId("v1")
                .chunkIndex(0)
                .content("Employees get 20 days of annual leave.")
                .score(0.85f)
                .build();

        when(retrieverService.retrieve("vacation")).thenReturn(List.of(chunk));
        when(promptService.buildPrompt(anyString(), anyList())).thenReturn("assembled prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequest);
        when(chatClientRequest.user(anyString())).thenReturn(chatClientRequest);
        when(chatClientRequest.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("You get 20 days.");

        ChatResponse response = ragChatService.chat("vacation");

        assertThat(response.answer()).isEqualTo("You get 20 days.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).documentName()).isEqualTo("policy.pdf");
        assertThat(response.sources().get(0).score()).isEqualTo(0.85f);
    }

    @Test
    void callLlm_whenChatClientThrows_wrapsInLlmException() {
        when(chatClient.prompt()).thenReturn(chatClientRequest);
        when(chatClientRequest.user(anyString())).thenReturn(chatClientRequest);
        when(chatClientRequest.call()).thenThrow(new RuntimeException("Bedrock timeout"));

        assertThatThrownBy(() -> ragChatService.callLlm("some prompt"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Bedrock LLM call failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void chat_sourcesMappedCorrectly() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .documentName("handbook.pdf")
                .versionId("v2")
                .chunkIndex(3)
                .content("Remote work policy details.")
                .score(0.92f)
                .build();

        when(retrieverService.retrieve(anyString())).thenReturn(List.of(chunk));
        when(promptService.buildPrompt(anyString(), anyList())).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequest);
        when(chatClientRequest.user(anyString())).thenReturn(chatClientRequest);
        when(chatClientRequest.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Remote work is allowed 3 days/week.");

        ChatResponse response = ragChatService.chat("remote work?");

        assertThat(response.sources()).hasSize(1);
        var source = response.sources().get(0);
        assertThat(source.documentName()).isEqualTo("handbook.pdf");
        assertThat(source.versionId()).isEqualTo("v2");
        assertThat(source.chunkIndex()).isEqualTo(3);
        assertThat(source.score()).isEqualTo(0.92f);
    }
}