package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.LLMService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class GeminiLLMService implements LLMService {

    private static final String PROMPT_TEMPLATE = """
            Processe o laudo médico abaixo seguindo estritamente as instruções de sistema.

            Retorne apenas JSON válido, sem texto antes ou depois, usando exatamente esta estrutura:
            {
              "summary": "Resumo clínico em texto plano",
              "details": "Achados detalhados em texto plano",
              "recommendation": "Recomendação e próximos passos em texto plano",
              "legal": "Aviso legal em texto plano"
            }

            Regras obrigatórias para os valores do JSON:
            - Use texto plano, sem Markdown.
            - Não use #, ##, ###, **, __, bullet points com asterisco, blocos de código ou tabelas.
            - Não repita os nomes dos campos dentro dos valores.
            - Se precisar separar ideias, use frases curtas ou quebras de linha simples dentro da string.

            Laudo original:
            %s
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String systemInstruction;

    public GeminiLLMService(
            @Value("${google.gemini.api-key:}") String apiKey,
            @Value("${google.gemini.model:gemini-1.5-flash}") String model,
            ResourceLoader resourceLoader
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.systemInstruction = loadSystemInstruction(resourceLoader);
    }

    private String loadSystemInstruction(ResourceLoader resourceLoader) {
        try {
            Resource resource = resourceLoader.getResource("classpath:gemini-system-instructions.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Gemini system instructions", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String simplifyMedicalReport(String content) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

		Map<String, Object> body = getStringObjectMap(content);

		Map<String, Object> response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        try {
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.getFirst();
                    Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
                    return (String) parts.getFirst().get("text");
                }
            }
            throw new IllegalStateException("Gemini response did not include generated content");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        }
    }

	private @NonNull Map<String, Object> getStringObjectMap(String content) {
		String prompt = PROMPT_TEMPLATE.formatted(content);
		Map<String, Object> body = Map.of(
				"contents", List.of(Map.of(
						"role", "user",
						"parts", List.of(Map.of("text", prompt))
				)),
				"system_instruction", Map.of(
						"parts", List.of(Map.of("text", systemInstruction))
				),
				"generationConfig", Map.of(
						"responseMimeType", "application/json"
				)
		);
		return body;
	}
}
