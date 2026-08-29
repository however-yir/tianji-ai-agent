package com.tianji.aigc.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight repository-backed prompt registry.
 *
 * <p>Baselines live under {@code classpath:prompts/<agentId>/v<n>.md} so they are code
 * reviewed and checksummed. External configuration (Nacos) is a controlled override on
 * top: {@link #resolve(String, String)} reports {@code overridden=true} and checksums the
 * override content, so a run can always say which prompt actually ran.
 */
@Component
public class PromptRegistry {

    public static final String BASELINE_PATH = "classpath:prompts/**/v*.md";

    private static final Pattern VERSION = Pattern.compile("v(\\d+)\\.md$");

    private final Map<String, PromptSpec> active = new LinkedHashMap<>();

    private final Map<String, Map<Integer, PromptSpec>> versions = new LinkedHashMap<>();

    public PromptRegistry() {
    }

    @PostConstruct
    public void init() {
        loadBaselines();
    }

    public Optional<PromptSpec> active(String promptId) {
        return Optional.ofNullable(active.get(promptId));
    }

    public Map<String, PromptSpec> allActive() {
        return Map.copyOf(active);
    }

    public int size() {
        return active.size();
    }

    /**
     * Resolve the effective prompt: external override wins; otherwise the repository baseline.
     */
    public PromptResolution resolve(String promptId, String overrideContent) {
        PromptSpec baseline = active.get(promptId);
        if (baseline == null) {
            throw new IllegalArgumentException("unknown promptId: " + promptId);
        }
        if (StringUtils.hasText(overrideContent)) {
            return new PromptResolution(promptId, baseline.version() + "+override", overrideContent,
                    checksum(overrideContent), true);
        }
        return new PromptResolution(promptId, baseline.version(), baseline.content(),
                baseline.checksum(), false);
    }

    /**
     * Trace what actually ran: baseline metadata + checksum of the effective (possibly
     * overridden / augmented) text. Overridden=true means the running text differs from
     * the repository baseline (external config or a fixed safety segment).
     */
    public PromptResolution trace(String promptId, String actualText) {
        PromptSpec baseline = active.get(promptId);
        if (baseline == null) {
            throw new IllegalArgumentException("unknown promptId: " + promptId);
        }
        boolean overridden = !baseline.content().equals(actualText);
        return new PromptResolution(promptId, baseline.version(), actualText,
                checksum(actualText), overridden);
    }

    public static String checksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void loadBaselines() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(BASELINE_PATH);
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                Matcher matcher = VERSION.matcher(path);
                if (!matcher.find()) {
                    continue;
                }
                String promptId = promptIdOf(path);
                int versionNo = Integer.parseInt(matcher.group(1));
                String version = "v" + versionNo;
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                PromptSpec spec = new PromptSpec(promptId, version, content,
                        "prompt " + promptId, checksum(content));
                versions.computeIfAbsent(promptId, k -> new LinkedHashMap<>()).put(versionNo, spec);
                active.merge(promptId, spec, (existing, candidate) ->
                        versionNoGreater(candidate, existing) ? candidate : existing);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("无法加载 prompts 基线: " + e.getMessage(), e);
        }
    }

    private boolean versionNoGreater(PromptSpec candidate, PromptSpec existing) {
        int candidateNo = Integer.parseInt(candidate.version().substring(1));
        int existingNo = Integer.parseInt(existing.version().substring(1));
        return candidateNo > existingNo;
    }

    private String promptIdOf(String path) {
        int slash = path.lastIndexOf('/');
        int dirStart = path.lastIndexOf('/', slash - 1);
        return path.substring(dirStart + 1, slash);
    }
}
