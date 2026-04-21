package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.entity.ExtractedText;
import br.com.rafhaelfreitas.tcc_project.domain.entity.GeneratedReport;
import br.com.rafhaelfreitas.tcc_project.domain.repository.ExtractedTextRepository;
import br.com.rafhaelfreitas.tcc_project.domain.repository.GeneratedReportRepository;
import br.com.rafhaelfreitas.tcc_project.domain.service.LLMService;
import br.com.rafhaelfreitas.tcc_project.domain.service.OcrService;
import br.com.rafhaelfreitas.tcc_project.domain.service.PdfGenerationService;
import br.com.rafhaelfreitas.tcc_project.domain.service.dto.GenerateReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateReportServiceImplTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private LLMService llmService;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private ExtractedTextRepository extractedTextRepository;

    @Mock
    private GeneratedReportRepository generatedReportRepository;

    @InjectMocks
    private GenerateReportServiceImpl generateReportService;

    @Test
    void shouldUseNativeExtractionWhenPdfContainsText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "native.pdf",
                "application/pdf",
                createPdfWithText("Texto nativo do laudo")
        );

        when(extractedTextRepository.save(any(ExtractedText.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmService.simplifyMedicalReport("Texto nativo do laudo")).thenReturn("Laudo simplificado");
        when(pdfGenerationService.generatePdfFromText("Laudo simplificado")).thenReturn("pdf-output".getBytes());

        GenerateReportResponse response = generateReportService.generateReport(file);

        assertEquals("Laudo simplificado", response.content());
        assertEquals(Base64.getEncoder().encodeToString("pdf-output".getBytes()), response.file());
        verify(ocrService, never()).extractTextFromScannedPdf(any());
    }

    @Test
    void shouldUseOcrWhenPdfHasNoNativeText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scan.pdf",
                "application/pdf",
                createBlankPdf()
        );

        when(ocrService.extractTextFromScannedPdf(any())).thenReturn("Texto OCR");
        when(extractedTextRepository.save(any(ExtractedText.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmService.simplifyMedicalReport("Texto OCR")).thenReturn("Resumo OCR");
        when(pdfGenerationService.generatePdfFromText("Resumo OCR")).thenReturn("ocr-pdf".getBytes());

        GenerateReportResponse response = generateReportService.generateReport(file);

        assertEquals("Resumo OCR", response.content());
        assertEquals(Base64.getEncoder().encodeToString("ocr-pdf".getBytes()), response.file());
        verify(ocrService).extractTextFromScannedPdf(any());
    }

    private byte[] createBlankPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
