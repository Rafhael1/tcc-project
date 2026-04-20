package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.OcrService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleVisionOcrService implements OcrService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GoogleVisionOcrService(
            ObjectMapper objectMapper,
            @Value("${google.vision.api-key:}") String apiKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://vision.googleapis.com")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String extractTextFromScannedPdf(byte[] pdfBytes) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Google Vision API key is not configured");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder allText = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 220, ImageType.RGB);
                String pageText = callVisionApi(image);
                if (StringUtils.hasText(pageText)) {
                    allText.append(pageText).append(System.lineSeparator());
                }
            }
            return allText.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render PDF pages for OCR", ex);
        }
    }

    private String callVisionApi(BufferedImage image) throws IOException {
        String imageBase64 = toBase64(image);
        Map<String, Object> body = Map.of(
                "requests", List.of(Map.of(
                        "image", Map.of("content", imageBase64),
                        "features", List.of(Map.of("type", "DOCUMENT_TEXT_DETECTION"))
                ))
        );

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/images:annotate")
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode firstResponse = root.path("responses").path(0);
            String fullText = firstResponse.path("fullTextAnnotation").path("text").asText();
            if (StringUtils.hasText(fullText)) {
                return fullText;
            }
            return firstResponse.path("textAnnotations").path(0).path("description").asText("");
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to parse Google Vision OCR response", ex);
        }
    }

    private String toBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
