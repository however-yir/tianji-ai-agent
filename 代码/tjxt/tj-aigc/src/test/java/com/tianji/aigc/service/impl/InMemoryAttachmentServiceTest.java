package com.tianji.aigc.service.impl;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.vo.AttachmentUploadVO;
import com.tianji.common.utils.UserContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAttachmentServiceTest {

    private final InMemoryAttachmentService attachmentService = new InMemoryAttachmentService();

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void shouldUploadTextAttachmentAndBuildRelevantContext() {
        UserContext.setUser(1001L);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "brief.txt",
                "text/plain",
                "课程咨询流程需要先识别用户目标，再补充预算、时间和风险提示。".getBytes(StandardCharsets.UTF_8)
        );

        List<AttachmentUploadVO> uploaded = attachmentService.upload(List.of(file));
        AttachmentContext context = attachmentService.buildContext(
                List.of(uploaded.get(0).getAttachmentId()),
                "请总结课程咨询里的风险提示"
        );

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).getPreviewText()).contains("课程咨询流程");
        assertThat(context.hasSources()).isTrue();
        assertThat(context.getSources().get(0).getAttachmentName()).isEqualTo("brief.txt");
        assertThat(context.getSources().get(0).getExcerpt()).contains("风险提示");
    }

    @Test
    void shouldParsePdfAndDocxAttachments() throws IOException {
        UserContext.setUser(1002L);
        MockMultipartFile pdf = new MockMultipartFile(
                "files",
                "roadmap.pdf",
                "application/pdf",
                createPdfBytes("Project roadmap focuses on docker compose and test automation.")
        );
        MockMultipartFile docx = new MockMultipartFile(
                "files",
                "summary.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocxBytes("Attachment summary highlights file upload, chunk retrieval and citations.")
        );

        List<AttachmentUploadVO> uploaded = attachmentService.upload(List.of(pdf, docx));
        AttachmentContext context = attachmentService.buildContext(
                uploaded.stream().map(AttachmentUploadVO::getAttachmentId).toList(),
                "Which attachment talks about citations?"
        );

        assertThat(uploaded).hasSize(2);
        assertThat(uploaded).extracting(AttachmentUploadVO::getName)
                .containsExactlyInAnyOrder("roadmap.pdf", "summary.docx");
        assertThat(uploaded).extracting(AttachmentUploadVO::getChunkCount)
                .allMatch(count -> count >= 1);
        assertThat(context.hasSources()).isTrue();
        assertThat(context.getSources())
                .extracting(source -> source.getExcerpt().toLowerCase())
                .anyMatch(text -> text.contains("citations"));
    }

    private byte[] createPdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createDocxBytes(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
