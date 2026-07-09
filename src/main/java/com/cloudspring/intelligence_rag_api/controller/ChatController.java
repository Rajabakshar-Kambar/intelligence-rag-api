package com.cloudspring.intelligence_rag_api.controller;

import com.cloudspring.intelligence_rag_api.dto.ChatRequest;
import com.cloudspring.intelligence_rag_api.dto.ChatResponse;
import com.cloudspring.intelligence_rag_api.dto.ErrorResponse;
import com.cloudspring.intelligence_rag_api.service.RagChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "RAG-powered HR assistant chat endpoint")
@SecurityRequirement(name = "ApiKey")
public class ChatController {

    private final RagChatService chatService;

    @PostMapping
    @Operation(
            summary = "Ask a question",
            description = "Retrieves relevant HR documents from Qdrant and generates a "
                    + "grounded answer using Amazon Bedrock Claude."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful answer",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "502", description = "Upstream service error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request.question());
        return ResponseEntity.ok(response);
    }
}
