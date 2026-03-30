package com.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class TextExtractionService {

    public String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        log.info("Extracting text from: {}, type: {}", filename, contentType);

        if (filename == null) {
            throw new IllegalArgumentException("Filename is required");
        }

        if (filename.toLowerCase().endsWith(".pdf") ||
                (contentType != null && contentType.contains("pdf"))) {
            return extractFromPdf(file.getInputStream());
        }
        else if (filename.toLowerCase().endsWith(".docx") ||
                (contentType != null && contentType.contains("word"))) {
            return extractFromDocx(file.getInputStream());
        }
        else if (filename.toLowerCase().endsWith(".txt") ||
                (contentType != null && contentType.contains("text"))) {
            return new String(file.getBytes(), "UTF-8");
        }
        else {
            throw new IllegalArgumentException("Unsupported file type: " + filename +
                    ". Supported: PDF, DOCX, TXT");
        }
    }

    private String extractFromPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = Loader.loadPDF((RandomAccessRead) inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        }
    }

    private String extractFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText).append("\n");
                }
            }
            log.info("Extracted {} characters from DOCX", text.length());
            return text.toString();
        }
    }
}