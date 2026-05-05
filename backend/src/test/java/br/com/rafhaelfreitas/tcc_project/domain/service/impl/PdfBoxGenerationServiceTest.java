package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxGenerationServiceTest {

    private final PdfBoxGenerationService pdfGenerationService = new PdfBoxGenerationService();

    @Test
    void shouldRenderStructuredJsonAsReadableReport() throws Exception {
        String content = """
                {
                  "summary": "Exame sem alterações críticas.",
                  "details": "Os achados foram descritos em linguagem simples.",
                  "recommendation": "Levar o resultado ao médico responsável.",
                  "legal": "Este documento não substitui avaliação profissional."
                }
                """;

        byte[] pdfBytes = pdfGenerationService.generatePdfFromText(content);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String pdfText = new PDFTextStripper().getText(document);

            assertTrue(pdfText.contains("Laudo simplificado"));
            assertTrue(pdfText.contains("Resumo"));
            assertTrue(pdfText.contains("Exame sem alterações críticas."));
            assertTrue(pdfText.contains("Detalhes explicados"));
            assertTrue(pdfText.contains("Levar o resultado ao médico responsável."));
            assertFalse(pdfText.contains("\"summary\""));
            assertFalse(pdfText.contains("{"));
        }
    }
}
