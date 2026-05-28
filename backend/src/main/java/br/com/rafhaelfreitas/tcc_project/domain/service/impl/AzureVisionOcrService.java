package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.OcrService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class AzureVisionOcrService implements OcrService {

    private static final String SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key";
    private static final int POLL_MAX_ATTEMPTS = 20;
    private static final long POLL_INTERVAL_MS = 500L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AzureVisionOcrService(
            ObjectMapper objectMapper,
            @Value("${azure.vision.endpoint:}") String endpoint,
            @Value("${azure.vision.api-key:}") String apiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(endpoint).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String extractTextFromScannedPdf(byte[] pdfBytes) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Azure Vision API key is not configured");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder allText = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 220, ImageType.RGB);
                String pageText = extractFromImage(image);
                if (StringUtils.hasText(pageText)) {
                    allText.append(pageText).append(System.lineSeparator());
                }
            }
            return allText.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render PDF pages for OCR", ex);
        }
    }

    private String extractFromImage(BufferedImage image) throws IOException {
        byte[] imageBytes = toPng(image);

        ResponseEntity<Void> response = restClient.post()
                .uri("/vision/v3.2/read/analyze")
                .header(SUBSCRIPTION_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(imageBytes)
                .retrieve()
                .toBodilessEntity();

        String operationLocation = response.getHeaders().getFirst("Operation-Location");
        if (!StringUtils.hasText(operationLocation)) {
            throw new IllegalStateException("Azure Vision OCR did not return operation location");
        }

        for (int attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
            String operationResponse = restClient.get()
                    .uri(operationLocation)
                    .header(SUBSCRIPTION_KEY_HEADER, apiKey)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(operationResponse);
            String status = root.path("status").asText("").toLowerCase();
            if ("succeeded".equals(status)) {
                return readExtractedText(root);
            }
            if ("failed".equals(status) || "canceled".equals(status)) {
                throw new IllegalStateException("Azure Vision OCR operation failed");
            }

            sleepPollingInterval();
        }

        throw new IllegalStateException("Azure Vision OCR operation timed out");
    }

    private String readExtractedText(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        JsonNode readResults = root.path("analyzeResult").path("readResults");
        for (JsonNode page : readResults) {
            for (JsonNode line : page.path("lines")) {
                String text = line.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    builder.append(text).append(System.lineSeparator());
                }
            }
        }
        return builder.toString().strip();
    }

    private void sleepPollingInterval() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling Azure Vision OCR operation", ex);
        }
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
