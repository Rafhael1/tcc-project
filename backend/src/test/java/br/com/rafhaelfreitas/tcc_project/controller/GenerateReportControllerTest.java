package br.com.rafhaelfreitas.tcc_project.controller;

import br.com.rafhaelfreitas.tcc_project.exception.GlobalExceptionHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerateReportController.class)
@Import(GlobalExceptionHandler.class)
class GenerateReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAcceptValidPng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                "png-content".getBytes()
        );

        mockMvc.perform(multipart("/generate-report").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Arquivo válido para processamento"));
    }

    @Test
    void shouldRejectUnsupportedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                "text/plain",
                "invalid".getBytes()
        );

        mockMvc.perform(multipart("/generate-report").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Tipo de arquivo inválido. Tipos aceitos: pdf, png, jpeg, webp"));
    }

    @Test
    void shouldRejectPdfWithMoreThanThreePages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                createPdfWithPages(4)
        );

        mockMvc.perform(multipart("/generate-report").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PDF excede o limite máximo de 3 páginas"));
    }

    @Test
    void shouldAcceptPdfWithThreePages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                createPdfWithPages(3)
        );

        mockMvc.perform(multipart("/generate-report").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Arquivo válido para processamento"));
    }

    @Test
    void shouldRejectWhenMultipartPartIsMissing() throws Exception {
        mockMvc.perform(multipart("/generate-report"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required multipart part 'file' is missing"));
    }

    private byte[] createPdfWithPages(int pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
