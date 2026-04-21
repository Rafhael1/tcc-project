package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.LLMService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GeminiLLMService implements LLMService {

    private static final String PROMPT_TEMPLATE = """
            Simplifique o laudo médico abaixo em linguagem clara para paciente, mantendo precisão clínica,
            sem inventar informações e preservando os achados relevantes.

            Laudo original:
            %s
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiLLMService(
            ObjectMapper objectMapper,
            @Value("${google.gemini.api-key:}") String apiKey,
            @Value("${google.gemini.model:gemini-1.5-flash}") String model
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String simplifyMedicalReport(String content) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String prompt = PROMPT_TEMPLATE.formatted(content);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String generatedText = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            if (!StringUtils.hasText(generatedText)) {
                throw new IllegalStateException("Gemini response did not include generated content");
            }
            return generatedText.strip();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        }
    }
}
