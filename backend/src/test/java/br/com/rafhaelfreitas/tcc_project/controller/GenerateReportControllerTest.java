package br.com.rafhaelfreitas.tcc_project.controller;

import br.com.rafhaelfreitas.tcc_project.domain.service.GenerateReportService;
import br.com.rafhaelfreitas.tcc_project.domain.service.dto.GenerateReportResponse;
import br.com.rafhaelfreitas.tcc_project.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerateReportController.class)
@Import(GlobalExceptionHandler.class)
class GenerateReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenerateReportService generateReportService;

    @Test
    void shouldAcceptValidPdfAndReturnFileAndContent() throws Exception {
        when(generateReportService.generateReport(any()))
                .thenReturn(new GenerateReportResponse("pdf-base64-content", "laudo simplificado"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "valid-pdf".getBytes()
        );

        mockMvc.perform(multipart("/generate-report").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file").value("pdf-base64-content"))
                .andExpect(jsonPath("$.content").value("laudo simplificado"));
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
                        .value("Tipo de arquivo inválido. Apenas PDF é aceito"));
    }

    @Test
    void shouldRejectWhenMultipartPartIsMissing() throws Exception {
        mockMvc.perform(multipart("/generate-report"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required multipart part 'file' is missing"));
    }
}
