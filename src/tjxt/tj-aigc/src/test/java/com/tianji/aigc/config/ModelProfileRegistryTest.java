package com.tianji.aigc.config;

import com.tianji.aigc.enums.AgentTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProfileRegistryTest {

    private ModelProfileProperties props() {
        ModelProfileProperties p = new ModelProfileProperties();
        ModelProfileProperties.Profile router = new ModelProfileProperties.Profile();
        router.setModel("qwen-turbo");
        router.setTemperature(0.1);
        ModelProfileProperties.Profile knowledge = new ModelProfileProperties.Profile();
        knowledge.setModel("qwen-max");
        p.getProfiles().put("router", router);
        p.getProfiles().put("knowledge", knowledge);
        return p;
    }

    @Test
    void shouldResolveConfiguredMapping() {
        ModelProfileProperties p = props();
        p.getAgentMapping().put("KNOWLEDGE", "knowledge");
        ModelProfileRegistry registry = new ModelProfileRegistry(p);

        assertThat(registry.resolve(AgentTypeEnum.ROUTE).orElseThrow().getModel()).isEqualTo("qwen-turbo");
        assertThat(registry.resolve(AgentTypeEnum.KNOWLEDGE).orElseThrow().getModel()).isEqualTo("qwen-max");
    }

    @Test
    void shouldFallbackToDefaultMapping() {
        ModelProfileRegistry registry = new ModelProfileRegistry(props());

        // RECOMMEND maps to general by default -> no general profile configured -> knowledge is
        // last resort in resolve(); here we only assert the static key decision
        assertThat(ModelProfileRegistry.defaultProfileKey(AgentTypeEnum.ROUTE)).isEqualTo("router");
        assertThat(ModelProfileRegistry.defaultProfileKey(AgentTypeEnum.RECOMMEND)).isEqualTo("general");
        assertThat(ModelProfileRegistry.defaultProfileKey(AgentTypeEnum.BUY)).isEqualTo("general");
        assertThat(ModelProfileRegistry.defaultProfileKey(AgentTypeEnum.KNOWLEDGE)).isEqualTo("knowledge");
    }
}
