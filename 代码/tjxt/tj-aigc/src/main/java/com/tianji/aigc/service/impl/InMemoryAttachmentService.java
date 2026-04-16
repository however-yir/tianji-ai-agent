package com.tianji.aigc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.attachment.AttachmentChunk;
import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentDocument;
import com.tianji.aigc.attachment.AttachmentSource;
import com.tianji.aigc.service.AttachmentService;
import com.tianji.aigc.vo.AttachmentUploadVO;
import com.tianji.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InMemoryAttachmentService implements AttachmentService {

    private static final long EXPIRE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024;
    private static final int MAX_SOURCES = 4;
    private static final int CHUNK_SIZE = 420;
    private static final int CHUNK_OVERLAP = 80;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,}");

    private final Map<String, AttachmentDocument> documentStore = new ConcurrentHashMap<>();
    private final Map<String, Long> expireAtStore = new ConcurrentHashMap<>();

    @Override
    public List<AttachmentUploadVO> upload(List<MultipartFile> files) {
        cleanupExpired();
        if (CollUtil.isEmpty(files)) {
            return List.of();
        }
        Long userId = currentUserId();
        List<AttachmentUploadVO> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateFile(file);
            String attachmentId = IdUtil.fastSimpleUUID();
            String extractedText = extractText(file);
            List<AttachmentChunk> chunks = chunkText(extractedText);
            AttachmentDocument document = AttachmentDocument.builder()
                    .attachmentId(attachmentId)
                    .userId(userId)
                    .fileName(StrUtil.blankToDefault(file.getOriginalFilename(), attachmentId))
                    .contentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                    .size(file.getSize())
                    .extractedText(extractedText)
                    .chunks(chunks)
                    .createdAt(LocalDateTime.now())
                    .build();
            documentStore.put(attachmentId, document);
            expireAtStore.put(attachmentId, System.currentTimeMillis() + EXPIRE_MILLIS);
            responses.add(AttachmentUploadVO.builder()
                    .attachmentId(attachmentId)
                    .name(document.getFileName())
                    .contentType(document.getContentType())
                    .size(document.getSize())
                    .previewText(preview(document.getExtractedText()))
                    .chunkCount(chunks.size())
                    .build());
        }
        return responses;
    }

    @Override
    public AttachmentContext buildContext(List<String> attachmentIds, String question) {
        cleanupExpired();
        if (CollUtil.isEmpty(attachmentIds)) {
            return AttachmentContext.builder()
                    .attachmentIds(List.of())
                    .attachmentNames(List.of())
                    .sources(List.of())
                    .build();
        }
        Long userId = currentUserId();
        List<AttachmentDocument> documents = attachmentIds.stream()
                .map(documentStore::get)
                .filter(Objects::nonNull)
                .filter(document -> Objects.equals(document.getUserId(), userId))
                .toList();
        if (documents.isEmpty()) {
            return AttachmentContext.builder()
                    .attachmentIds(List.of())
                    .attachmentNames(List.of())
                    .sources(List.of())
                    .build();
        }

        List<ScoredChunk> scoredChunks = scoreChunks(documents, question);
        List<AttachmentSource> sources = scoredChunks.stream()
                .limit(MAX_SOURCES)
                .map(chunk -> AttachmentSource.builder()
                        .attachmentId(chunk.document().getAttachmentId())
                        .attachmentName(chunk.document().getFileName())
                        .chunkIndex(chunk.chunk().getIndex())
                        .score(chunk.score())
                        .excerpt(preview(chunk.chunk().getContent()))
                        .build())
                .toList();

        return AttachmentContext.builder()
                .attachmentIds(documents.stream().map(AttachmentDocument::getAttachmentId).toList())
                .attachmentNames(documents.stream().map(AttachmentDocument::getFileName).toList())
                .sources(sources)
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("附件超过 8MB，请压缩后再上传。");
        }
    }

    private String extractText(MultipartFile file) {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment");
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".pdf")) {
                return normalizeText(extractPdfText(file));
            }
            if (lowerName.endsWith(".docx")) {
                return normalizeText(extractDocxText(file));
            }
            if (isImage(file, lowerName)) {
                return normalizeText(extractImageText(file));
            }
            return normalizeText(extractPlainText(file));
        } catch (IOException e) {
            throw new IllegalStateException("解析附件失败：" + fileName, e);
        }
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocxText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream(); XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (StrUtil.isNotBlank(paragraph.getText())) {
                    builder.append(paragraph.getText()).append('\n');
                }
            }
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells().forEach(cell -> {
                if (StrUtil.isNotBlank(cell.getText())) {
                    builder.append(cell.getText()).append('\n');
                }
            })));
            return builder.toString();
        }
    }

    private String extractPlainText(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String extractImageText(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("aigc-ocr-");
        Path imagePath = tempDir.resolve(StrUtil.blankToDefault(file.getOriginalFilename(), "image.png"));
        Path outputBase = tempDir.resolve("ocr-result");
        try {
            Files.write(imagePath, file.getBytes());
            Process process = new ProcessBuilder(
                    "tesseract",
                    imagePath.toString(),
                    outputBase.toString(),
                    "-l",
                    "chi_sim+eng"
            ).redirectErrorStream(true).start();
            String logs;
            try (InputStream inputStream = process.getInputStream()) {
                logs = IoUtil.read(inputStream, StandardCharsets.UTF_8);
            }
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("图片 OCR 处理中断", e);
            }
            Path resultPath = tempDir.resolve("ocr-result.txt");
            if (exitCode == 0 && Files.exists(resultPath)) {
                return Files.readString(resultPath, StandardCharsets.UTF_8);
            }
            log.warn("图片 OCR 失败，exitCode={}, logs={}", exitCode, logs);
            return "未能从图片中提取到清晰文字。";
        } catch (IOException e) {
            log.warn("当前环境未安装 tesseract，图片 OCR 将返回占位提示。 fileName={}", file.getOriginalFilename());
            return "当前运行环境未安装 OCR 引擎，暂时无法识别图片中的文字。";
        } finally {
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignore) {
                                // ignore cleanup failure
                            }
                        });
            } catch (IOException ignore) {
                // ignore cleanup failure
            }
        }
    }

    private boolean isImage(MultipartFile file, String lowerName) {
        String contentType = StrUtil.blankToDefault(file.getContentType(), "");
        return contentType.startsWith("image/")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".bmp");
    }

    private String normalizeText(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return "未提取到可用正文，建议检查文件内容或格式。";
        }
        return rawText
                .replace("\u0000", "")
                .replaceAll("\\r", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \t]{2,}", " ")
                .trim();
    }

    private List<AttachmentChunk> chunkText(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of(AttachmentChunk.builder()
                    .id(IdUtil.fastSimpleUUID())
                    .index(1)
                    .content("未提取到正文")
                    .build());
        }
        String normalized = normalizeText(text);
        if (normalized.length() <= CHUNK_SIZE) {
            return List.of(AttachmentChunk.builder()
                    .id(IdUtil.fastSimpleUUID())
                    .index(1)
                    .content(normalized)
                    .build());
        }
        List<String> paragraphs = Arrays.stream(normalized.split("\\n\\n+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
        List<String> chunkContents = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > CHUNK_SIZE) {
                chunkContents.add(current.toString().trim());
                String overlap = current.length() > CHUNK_OVERLAP
                        ? current.substring(Math.max(0, current.length() - CHUNK_OVERLAP))
                        : current.toString();
                current = new StringBuilder(overlap).append('\n').append(paragraph);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(paragraph);
            }
        }
        if (current.length() > 0) {
            chunkContents.add(current.toString().trim());
        }
        List<AttachmentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkContents.size(); i++) {
            chunks.add(AttachmentChunk.builder()
                    .id(IdUtil.fastSimpleUUID())
                    .index(i + 1)
                    .content(chunkContents.get(i))
                    .build());
        }
        return chunks;
    }

    private List<ScoredChunk> scoreChunks(List<AttachmentDocument> documents, String question) {
        Set<String> queryTokens = tokenize(question);
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (AttachmentDocument document : documents) {
            for (AttachmentChunk chunk : document.getChunks()) {
                double score = scoreChunk(queryTokens, chunk.getContent(), chunk.getIndex());
                scoredChunks.add(new ScoredChunk(document, chunk, score));
            }
        }
        return scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(item -> item.chunk().getIndex()))
                .filter(item -> item.score() > 0)
                .collect(Collectors.collectingAndThen(Collectors.toList(), matches -> {
                    if (CollUtil.isNotEmpty(matches)) {
                        return deduplicate(matches);
                    }
                    return deduplicate(documents.stream()
                            .flatMap(document -> document.getChunks().stream().limit(1)
                                    .map(chunk -> new ScoredChunk(document, chunk, 0.1)))
                            .toList());
                }));
    }

    private List<ScoredChunk> deduplicate(List<ScoredChunk> chunks) {
        Set<String> seen = new LinkedHashSet<>();
        List<ScoredChunk> deduped = new ArrayList<>();
        for (ScoredChunk chunk : chunks) {
            String key = chunk.document().getAttachmentId() + "#" + chunk.chunk().getIndex();
            if (seen.add(key)) {
                deduped.add(chunk);
            }
        }
        return deduped;
    }

    private double scoreChunk(Set<String> queryTokens, String content, int chunkIndex) {
        if (CollUtil.isEmpty(queryTokens)) {
            return 0.2 / chunkIndex;
        }
        Set<String> contentTokens = tokenize(content);
        long matches = queryTokens.stream().filter(contentTokens::contains).count();
        return matches + (0.05 / Math.max(1, chunkIndex));
    }

    private Set<String> tokenize(String text) {
        if (StrUtil.isBlank(text)) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            tokens.add(token);
            if (containsHan(token) && token.length() > 2) {
                for (int i = 0; i < token.length() - 1; i++) {
                    tokens.add(token.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean containsHan(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String preview(String content) {
        if (StrUtil.isBlank(content)) {
            return "未提取到可引用内容。";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return StrUtil.sub(normalized, 0, 180);
    }

    private Long currentUserId() {
        return UserContext.getUser() == null ? 0L : UserContext.getUser();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        expireAtStore.forEach((attachmentId, expireAt) -> {
            if (expireAt < now) {
                expireAtStore.remove(attachmentId);
                documentStore.remove(attachmentId);
            }
        });
    }

    private record ScoredChunk(AttachmentDocument document, AttachmentChunk chunk, double score) {
    }
}
