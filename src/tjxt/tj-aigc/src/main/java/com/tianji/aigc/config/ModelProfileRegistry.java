package com.tianji.aigc.config;

import com.tianji.aigc.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Deterministic agent-to-profile resolution. The mapping is configuration, not an
 * AI-driven router: ROUTE uses fast structured models, KNOWLEDGE uses the strongest
 * configured profile, everything else falls back to general.
 */
@Component
public class ModelProfileRegistry {

    private final ModelProfileProperties properties;

    public ModelProfileRegistry(ModelProfileProperties properties) {
        this.properties = properties;
    }

    public Optional<ModelProfileProperties.Profile> resolve(AgentTypeEnum agentType) {
        String profileKey = properties.getAgentMapping().get(agentType.name());
        if (profileKey == null) {
            profileKey = defaultProfileKey(agentType);
        }
        ModelProfileProperties.Profile profile = properties.getProfiles().get(profileKey);
        if (profile != null) {
            return Optional.of(profile);
        }
        return Optional.ofNullable(properties.getProfiles().get("general"));
    }

    public static String defaultProfileKey(AgentTypeEnum agentType) {
        return switch (agentType) {
            case ROUTE -> "router";
            case KNOWLEDGE, STUDY_PLAN -> "knowledge";
            default -> "general";
        };
    }

    /** Backing map for manifest/validation purposes. */
    public Map<String, ModelProfileProperties.Profile> profiles() {
        return Map.copyOf(properties.getProfiles());
    }
}
