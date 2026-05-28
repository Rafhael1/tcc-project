package br.com.rafhaelfreitas.tcc_project.domain.service;

import br.com.rafhaelfreitas.tcc_project.domain.service.dto.GenerateReportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface GenerateReportService {
	GenerateReportResponse generateReport(MultipartFile file);
}
