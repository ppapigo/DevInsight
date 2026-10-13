package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.*;

import com.portfolio.devinsight.common.ApiException;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.document.DocumentExtractor;
import java.io.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentExtractorTest {
  private final DocumentExtractor extractor = new DocumentExtractor(new AppProperties());

  @Test
  void extractsPdfText() throws Exception {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(40, 700);
        content.showText("Evidence from PDF");
        content.endText();
      }
      doc.save(out);
      assertThat(extractor.extract("report.pdf", out.toByteArray()).text())
          .contains("Evidence from PDF");
    }
  }

  @Test
  void extractsWordParagraphsAndTables() throws Exception {
    try (var doc = new XWPFDocument();
        var out = new ByteArrayOutputStream()) {
      doc.createParagraph().createRun().setText("Project architecture");
      doc.createTable().getRow(0).getCell(0).setText("Security evidence");
      doc.write(out);
      var text = extractor.extract("report.docx", out.toByteArray()).text();
      assertThat(text).contains("Project architecture", "Security evidence");
    }
  }

  @Test
  void extractsPowerPointText() throws Exception {
    try (var doc = new XMLSlideShow();
        var out = new ByteArrayOutputStream()) {
      doc.createSlide().createTextBox().setText("Architecture slide");
      doc.write(out);
      assertThat(extractor.extract("demo.pptx", out.toByteArray()).text())
          .contains("Architecture slide");
    }
  }

  @Test
  void rejectsDisguisedExecutablesAndMacroArchives() throws Exception {
    assertThatThrownBy(() -> extractor.extract("report.pdf", "MZ executable".getBytes()))
        .isInstanceOf(ApiException.class);
    try (var out = new ByteArrayOutputStream()) {
      try (var zip = new ZipOutputStream(out)) {
        zip.putNextEntry(new ZipEntry("word/vbaProject.bin"));
        zip.write(new byte[] {1});
        zip.closeEntry();
      }
      assertThatThrownBy(() -> extractor.extract("report.docx", out.toByteArray()))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void clipsTextAndRejectsBinaryTxt() {
    var p = new AppProperties();
    p.setMaxDocumentChars(100);
    assertThat(new DocumentExtractor(p).extract("notes.md", "text ".repeat(100).getBytes()).text())
        .hasSize(100);
    assertThatThrownBy(() -> extractor.extract("notes.txt", new byte[] {1, 0, 2}))
        .isInstanceOf(ApiException.class);
  }
}
