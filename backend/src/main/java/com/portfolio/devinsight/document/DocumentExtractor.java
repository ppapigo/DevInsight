package com.portfolio.devinsight.document;

import com.portfolio.devinsight.common.ApiException;
import com.portfolio.devinsight.config.AppProperties;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractor {
  public record Extracted(String contentType, String text, String extension) {}

  private final AppProperties props;

  public DocumentExtractor(AppProperties props) {
    this.props = props;
    ZipSecureFile.setMinInflateRatio(0.01);
    ZipSecureFile.setMaxEntrySize(20_000_000);
    ZipSecureFile.setMaxTextSize(4_000_000);
  }

  public Extracted extract(String name, byte[] data) {
    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (data.length == 0 || data.length > 10 * 1024 * 1024)
      throw ApiException.badRequest("빈 파일이거나 파일 크기가 10MB를 초과합니다.");
    try {
      return switch (ext) {
        case "txt", "md" -> {
          String text =
              StandardCharsets.UTF_8
                  .newDecoder()
                  .onMalformedInput(CodingErrorAction.REPORT)
                  .decode(java.nio.ByteBuffer.wrap(data))
                  .toString();
          if (text.indexOf('\0') >= 0) throw new IOException("Binary text");
          yield new Extracted("text/plain", clip(text), ext);
        }
        case "pdf" -> {
          if (!new String(data, 0, Math.min(5, data.length), StandardCharsets.US_ASCII)
              .equals("%PDF-")) throw new IOException("Invalid PDF");
          try (var pdf = Loader.loadPDF(data)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() > 200)
              throw new IOException("Unsupported PDF");
            var writer = new LimitedWriter(props.getMaxDocumentChars());
            try {
              new PDFTextStripper().writeText(pdf, writer);
            } catch (TextLimitReached ignored) {
            }
            yield new Extracted("application/pdf", writer.toString(), ext);
          }
        }
        case "docx" -> {
          inspectZip(data, "word/document.xml");
          try (var doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            var text = new StringBuilder();
            for (var p : doc.getParagraphs()) {
              append(text, p.getText());
              if (full(text)) break;
            }
            for (var t : doc.getTables()) {
              if (full(text)) break;
              append(text, t.getText());
            }
            yield new Extracted(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                clip(text.toString()),
                ext);
          }
        }
        case "pptx" -> {
          inspectZip(data, "ppt/presentation.xml");
          try (var ppt = new XMLSlideShow(new ByteArrayInputStream(data))) {
            var text = new StringBuilder();
            for (var slide : ppt.getSlides()) {
              if (full(text)) break;
              for (var shape : slide.getShapes())
                if (shape instanceof XSLFTextShape t) {
                  append(text, t.getText());
                  if (full(text)) break;
                }
            }
            yield new Extracted(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                clip(text.toString()),
                ext);
          }
        }
        case "png", "jpg", "jpeg" -> {
          try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Invalid image");
            var reader = readers.next();
            try {
              reader.setInput(in);
              String format = reader.getFormatName().toLowerCase(Locale.ROOT);
              if (!Set.of("png", "jpeg", "jpg").contains(format))
                throw new IOException("Invalid format");
              if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000L)
                throw new IOException("Image too large");
              boolean png = format.equals("png");
              if (png != ext.equals("png")) throw new IOException("Format mismatch");
              yield new Extracted(png ? "image/png" : "image/jpeg", "", ext);
            } finally {
              reader.dispose();
            }
          }
        }
        default ->
            throw ApiException.badRequest("PDF, DOCX, PPTX, UTF-8 TXT/Markdown, PNG, JPEG만 지원합니다.");
      };
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.badRequest("파일 형식이 올바르지 않거나 문서 처리 한도를 초과했습니다. 암호화·매크로·실행 파일은 지원하지 않습니다.");
    }
  }

  private void inspectZip(byte[] data, String required) throws IOException {
    long total = 0;
    int count = 0;
    boolean found = false;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
      java.util.zip.ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        if (++count > 2000
            || name.contains("vbaproject")
            || name.contains("embeddings/")
            || name.contains("../")) throw new IOException("Unsafe archive");
        if (entry.getName().equals(required)) found = true;
        int n;
        while ((n = zip.read(buffer)) != -1) {
          total += n;
          if (total > 30_000_000) throw new IOException("Archive limit");
        }
      }
    }
    if (!found) throw new IOException("Wrong Office format");
  }

  private String clip(String text) {
    return text.substring(0, Math.min(props.getMaxDocumentChars(), text.length()));
  }

  private boolean full(StringBuilder b) {
    return b.length() >= props.getMaxDocumentChars();
  }

  private void append(StringBuilder b, String s) {
    if (!full(b))
      b.append(s, 0, Math.min(s.length(), props.getMaxDocumentChars() - b.length())).append('\n');
  }

  private static class TextLimitReached extends IOException {}

  private static class LimitedWriter extends Writer {
    private final int max;
    private final StringBuilder text = new StringBuilder();

    LimitedWriter(int max) {
      this.max = max;
    }

    @Override
    public void write(char[] c, int off, int len) throws IOException {
      int n = Math.min(len, max - text.length());
      if (n > 0) text.append(c, off, n);
      if (n < len) throw new TextLimitReached();
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    @Override
    public String toString() {
      return text.toString();
    }
  }
}
