package br.com.rafhaelfreitas.tcc_project.controller;

import br.com.rafhaelfreitas.tcc_project.domain.service.GenerateReportService;
import br.com.rafhaelfreitas.tcc_project.domain.service.dto.GenerateReportResponse;
import br.com.rafhaelfreitas.tcc_project.exception.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@RestController
public class GenerateReportController {

    private final GenerateReportService generateReportService;

    public GenerateReportController(GenerateReportService generateReportService) {
        this.generateReportService = generateReportService;
    }

    @PostMapping(value = "/generate-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GenerateReportResponse generateReport(@RequestPart("file") MultipartFile file) {
        validateFile(file);
        return generateReportService.generateReport(file);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Arquivo é obrigatório");
        }

        String contentType = file.getContentType();
        String normalizedContentType = contentType == null ? null : contentType.toLowerCase(Locale.ROOT);
        if (!MediaType.APPLICATION_PDF_VALUE.equals(normalizedContentType)) {
            throw new BadRequestException("Tipo de arquivo inválido. Apenas PDF é aceito");
        }
    }
}
