package br.com.rafhaelfreitas.tcc_project.domain.service.impl;

import br.com.rafhaelfreitas.tcc_project.domain.service.PdfGenerationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfBoxGenerationService implements PdfGenerationService {

    private static final int FONT_SIZE = 12;
    private static final float LEADING = 16f;
    private static final float MARGIN = 50f;
    private static final int MAX_CHARS_PER_LINE = 95;

    @Override
    public byte[] generatePdfFromText(String content) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            List<String> lines = splitIntoLines(content == null ? "" : content);
            writeLines(document, lines);
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate PDF output", ex);
        }
    }

    private void writeLines(PDDocument document, List<String> lines) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDRectangle pageSize = PDRectangle.A4;
        float yStart = pageSize.getHeight() - MARGIN;
        float yLimit = MARGIN;

        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(font, FONT_SIZE);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yStart);

        float y = yStart;
        for (String line : lines) {
            if (y <= yLimit) {
                contentStream.endText();
                contentStream.close();

                page = new PDPage(pageSize);
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                contentStream.setFont(font, FONT_SIZE);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN, yStart);
                y = yStart;
            }

            contentStream.showText(line);
            contentStream.newLineAtOffset(0, -LEADING);
            y -= LEADING;
        }

        contentStream.endText();
        contentStream.close();
    }

    private List<String> splitIntoLines(String content) {
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

                if (current.length() + 1 + word.length() > MAX_CHARS_PER_LINE) {
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
}
