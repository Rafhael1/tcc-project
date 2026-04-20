package br.com.rafhaelfreitas.tcc_project.controller;

import br.com.rafhaelfreitas.tcc_project.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
public class GenerateReportController {

    private static final int MAX_PDF_PAGES = 3;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp"
    );

    @PostMapping(value = "/generate-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> generateReport(@RequestPart("file") MultipartFile file) {
        validateFile(file);
        return Map.of("message", "Arquivo válido para processamento");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Arquivo é obrigatório");
        }

        String contentType = file.getContentType();
        String normalizedContentType = contentType == null ? null : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType == null || !ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BadRequestException("Tipo de arquivo inválido. Tipos aceitos: pdf, png, jpeg, webp");
        }

        if (MediaType.APPLICATION_PDF_VALUE.equals(normalizedContentType)) {
            validatePdfPageLimit(file);
        }
    }

    private void validatePdfPageLimit(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new BadRequestException(String.format(
                        "PDF excede o limite máximo de %d páginas",
                        MAX_PDF_PAGES
                ));
            }
        } catch (IOException ex) {
            throw new BadRequestException("Falha ao processar o PDF enviado. Verifique se o arquivo não está corrompido.", ex);
        }
    }
}
