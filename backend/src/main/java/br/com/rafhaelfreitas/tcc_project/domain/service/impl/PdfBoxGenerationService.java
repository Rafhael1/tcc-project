package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.PdfGenerationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfBoxGenerationService implements PdfGenerationService {

    private static final float MARGIN = 52f;
    private static final float FOOTER_MARGIN = 32f;
    private static final float HEADER_HEIGHT = 82f;
    private static final float SECTION_GAP = 18f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float BODY_LEADING = 15f;
    private static final float SECTION_TITLE_SIZE = 13f;
    private static final float TITLE_SIZE = 20f;

    @Override
    public byte[] generatePdfFromText(String content) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeReport(document, ReportView.from(content));
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate PDF output", ex);
        }
    }

    private void writeReport(PDDocument document, ReportView report) throws IOException {
        PdfCursor cursor = new PdfCursor(document);
        cursor.writeHeader();

        cursor.writeSection("Resumo", report.summary());
        cursor.writeSection("Detalhes explicados", report.details());
        cursor.writeSection("Recomendacoes", report.recommendation());
        cursor.writeSection("Aviso legal", report.legal());

        cursor.close();
    }

    private static class PdfCursor {
        private final PDDocument document;
        private final PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont subtitleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        private final PDFont sectionFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDRectangle pageSize = PDRectangle.A4;
        private PDPageContentStream contentStream;
        private float y;
        private int pageNumber = 0;

        private PdfCursor(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void writeHeader() throws IOException {
            contentStream.setNonStrokingColor(new Color(17, 24, 39));
            contentStream.addRect(0, pageSize.getHeight() - HEADER_HEIGHT, pageSize.getWidth(), HEADER_HEIGHT);
            contentStream.fill();

            contentStream.beginText();
            contentStream.setNonStrokingColor(new Color(255, 255, 255));
            contentStream.setFont(titleFont, TITLE_SIZE);
            contentStream.newLineAtOffset(MARGIN, pageSize.getHeight() - 38);
            contentStream.showText("Laudo simplificado");
            contentStream.endText();

            contentStream.beginText();
            contentStream.setNonStrokingColor(new Color(203, 213, 225));
            contentStream.setFont(subtitleFont, 10f);
            contentStream.newLineAtOffset(MARGIN, pageSize.getHeight() - 58);
            contentStream.showText("Versao em linguagem clara gerada pelo MedScan TCC");
            contentStream.endText();

            y = pageSize.getHeight() - HEADER_HEIGHT - 34f;
        }

        private void writeSection(String title, String body) throws IOException {
            if (body == null || body.isBlank()) {
                return;
            }

            List<String> lines = splitIntoLines(body, bodyFont, BODY_FONT_SIZE, contentWidth());
            float neededHeight = SECTION_TITLE_SIZE + 9f + lines.size() * BODY_LEADING + SECTION_GAP;
            ensureSpace(neededHeight);

            contentStream.beginText();
            contentStream.setNonStrokingColor(new Color(37, 99, 235));
            contentStream.setFont(sectionFont, SECTION_TITLE_SIZE);
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText(title);
            contentStream.endText();
            y -= 22f;

            contentStream.beginText();
            contentStream.setNonStrokingColor(new Color(51, 65, 85));
            contentStream.setFont(bodyFont, BODY_FONT_SIZE);
            contentStream.newLineAtOffset(MARGIN, y);

            for (String line : lines) {
                contentStream.showText(sanitize(line));
                contentStream.newLineAtOffset(0, -BODY_LEADING);
                y -= BODY_LEADING;
            }

            contentStream.endText();
            y -= SECTION_GAP;
        }

        private void ensureSpace(float neededHeight) throws IOException {
            if (y - neededHeight >= FOOTER_MARGIN) {
                return;
            }

            newPage();
            y = pageSize.getHeight() - MARGIN;
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }

            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            pageNumber++;
            contentStream = new PDPageContentStream(document, page);
            y = pageSize.getHeight() - MARGIN;
            writeFooter();
        }

        private void writeFooter() throws IOException {
            contentStream.beginText();
            contentStream.setNonStrokingColor(new Color(148, 163, 184));
            contentStream.setFont(bodyFont, 8f);
            contentStream.newLineAtOffset(MARGIN, FOOTER_MARGIN - 8f);
            contentStream.showText("MedScan TCC - pagina " + pageNumber);
            contentStream.endText();
        }

        private void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
        }

        private float contentWidth() {
            return pageSize.getWidth() - (MARGIN * 2);
        }
    }

    private static List<String> splitIntoLines(String content, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : content.split("\\R")) {
            if (paragraph.isBlank()) {
                lines.add(" ");
                continue;
            }

            String[] words = paragraph.trim().split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (current.isEmpty()) {
                    current.append(word);
                    continue;
                }

                String candidate = current + " " + word;
                if (textWidth(candidate, font, fontSize) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.append(' ').append(word);
                }
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines.isEmpty() ? List.of(" ") : lines;
    }

    private static float textWidth(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(sanitize(text)) / 1000f * fontSize;
    }

    private static String sanitize(String text) {
        return text == null ? "" : text
                .replace("\t", " ")
                .replace("\u2013", "-")
                .replace("\u2014", "-")
                .replace("\u201c", "\"")
                .replace("\u201d", "\"")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replace("\u2022", "-");
    }

    private record ReportView(String summary, String details, String recommendation, String legal) {
        private static ReportView from(String content) {
            String normalized = normalizeJson(content);
            Optional<String> summary = jsonText(normalized, "summary");
            Optional<String> details = jsonText(normalized, "details");
            Optional<String> recommendation = jsonText(normalized, "recommendation");
            Optional<String> legal = jsonText(normalized, "legal");

            if (summary.isPresent() || details.isPresent() || recommendation.isPresent() || legal.isPresent()) {
                return new ReportView(
                        summary.orElse("Laudo processado com sucesso."),
                        details.orElse(normalized),
                        recommendation.orElse("Procure orientacao profissional para interpretar os resultados."),
                        legal.orElse("Este documento nao substitui a avaliacao de um profissional de saude.")
                );
            }

            return new ReportView(
                    "Laudo processado com sucesso.",
                    content == null || content.isBlank() ? "Nenhum conteudo foi gerado." : content,
                    "Procure orientacao profissional para interpretar os resultados.",
                    "Este documento nao substitui a avaliacao de um profissional de saude."
            );
        }

        private static String normalizeJson(String content) {
            String value = content == null ? "" : content.trim();
            if (value.startsWith("```")) {
                value = value.replaceFirst("^```(?:json)?\\s*", "");
                value = value.replaceFirst("\\s*```$", "");
            }
            return value;
        }

        private static Optional<String> jsonText(String content, String field) {
            Pattern pattern = Pattern.compile(
                    "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".formatted(Pattern.quote(field)),
                    Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) {
                return Optional.empty();
            }

            String value = matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", " ")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();

            return value.isBlank() ? Optional.empty() : Optional.of(sanitize(value));
        }
    }
}
