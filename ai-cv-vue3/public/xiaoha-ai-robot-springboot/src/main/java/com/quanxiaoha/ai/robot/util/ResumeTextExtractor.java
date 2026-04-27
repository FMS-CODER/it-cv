package com.quanxiaoha.ai.robot.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
public class ResumeTextExtractor {

    public static String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) {
            return extractFromPdf(file.getInputStream());
        } else if (lowerFilename.endsWith(".docx")) {
            return extractFromDocx(file.getInputStream());
        } else {
            throw new IllegalArgumentException("不支持的文件格式，请上传 PDF 或 DOCX 文件");
        }
    }

    public static String extractFromPdf(InputStream inputStream) throws IOException {
        log.info("开始解析 PDF 文件");
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDF 解析完成，文本长度: {}", text.length());
            return text;
        }
    }

    public static String extractFromDocx(InputStream inputStream) throws IOException {
        log.info("开始解析 DOCX 文件");
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text).append("\n");
                }
            }
            
            String result = sb.toString();
            log.info("DOCX 解析完成，文本长度: {}", result.length());
            return result;
        }
    }
}
