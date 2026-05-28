package br.com.rafhaelfreitas.tcc_project.domain.service;

public interface PdfGenerationService {
    byte[] generatePdfFromText(String content);
}
