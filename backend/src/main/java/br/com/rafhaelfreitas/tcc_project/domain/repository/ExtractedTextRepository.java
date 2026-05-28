package br.com.rafhaelfreitas.tcc_project.domain.repository;

import br.com.rafhaelfreitas.tcc_project.domain.entity.ExtractedText;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedTextRepository extends JpaRepository<ExtractedText, Long> {
}
