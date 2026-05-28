package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.entity.ExtractedText;
import br.com.rafhaelfreitas.tcc_project.domain.entity.GeneratedReport;
import br.com.rafhaelfreitas.tcc_project.domain.repository.ExtractedTextRepository;
import br.com.rafhaelfreitas.tcc_project.domain.repository.GeneratedReportRepository;
import br.com.rafhaelfreitas.tcc_project.domain.service.GenerateReportService;
import br.com.rafhaelfreitas.tcc_project.domain.service.LLMService;
import br.com.rafhaelfreitas.tcc_project.domain.service.OcrService;
import br.com.rafhaelfreitas.tcc_project.domain.service.PdfGenerationService;
import br.com.rafhaelfreitas.tcc_project.domain.service.dto.GenerateReportResponse;
import br.com.rafhaelfreitas.tcc_project.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@Service
public class GenerateReportServiceImpl implements GenerateReportService {

    private final OcrService ocrService;
    private final LLMService llmService;
    private final PdfGenerationService pdfGenerationService;
    private final ExtractedTextRepository extractedTextRepository;
    private final GeneratedReportRepository generatedReportRepository;

    public GenerateReportServiceImpl(
            OcrService ocrService,
            LLMService llmService,
            PdfGenerationService pdfGenerationService,
            ExtractedTextRepository extractedTextRepository,
            GeneratedReportRepository generatedReportRepository
    ) {
        this.ocrService = ocrService;
        this.llmService = llmService;
        this.pdfGenerationService = pdfGenerationService;
        this.extractedTextRepository = extractedTextRepository;
        this.generatedReportRepository = generatedReportRepository;
    }

    @Override
    @Transactional
    public GenerateReportResponse generateReport(MultipartFile file) {
        byte[] fileBytes = readFile(file);

        TextExtractionResult extractionResult = extractText(fileBytes);
        ExtractedText extractedText = saveExtractedText(file.getOriginalFilename(), extractionResult);

        String simplifiedContent = llmService.simplifyMedicalReport(extractionResult.content());
        GeneratedReport generatedReport = new GeneratedReport();
        generatedReport.setExtractedText(extractedText);
        generatedReport.setContent(simplifiedContent);
        generatedReportRepository.save(generatedReport);

        byte[] generatedPdf = pdfGenerationService.generatePdfFromText(simplifiedContent);
        String base64File = Base64.getEncoder().encodeToString(generatedPdf);

        return new GenerateReportResponse(base64File, simplifiedContent);
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Falha ao ler o PDF enviado", ex);
        }
    }

    private TextExtractionResult extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String nativeText = new PDFTextStripper().getText(document);
            if (nativeText != null && !nativeText.isBlank()) {
                return new TextExtractionResult(nativeText.strip(), "NATIVE");
            }
        } catch (IOException ex) {
            throw new BadRequestException(
                    "Falha ao processar o PDF enviado. Verifique se o arquivo não está corrompido ou inválido.",
                    ex
            );
        }

        String ocrText = ocrService.extractTextFromScannedPdf(pdfBytes);
        if (ocrText == null || ocrText.isBlank()) {
            throw new BadRequestException("Não foi possível extrair texto do PDF enviado");
        }
        return new TextExtractionResult(ocrText.strip(), "OCR");
    }

    private ExtractedText saveExtractedText(String originalFileName, TextExtractionResult extractionResult) {
        ExtractedText extractedText = new ExtractedText();
        extractedText.setSourceFileName(originalFileName == null ? "unknown.pdf" : originalFileName);
        extractedText.setSourceType(extractionResult.sourceType());
        extractedText.setContent(extractionResult.content());
        return extractedTextRepository.save(extractedText);
    }

    private record TextExtractionResult(String content, String sourceType) {
    }
}
