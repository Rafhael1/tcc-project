package br.com.rafhaelfreitas.tcc_project.domain.service;

public interface OcrService {
    String extractTextFromScannedPdf(byte[] pdfBytes);
}
