package com.tianji.aigc.tools;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseToolsTest {

    @Mock
    private CourseClient courseClient;

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-1");
    }

    @Test
    void shouldQueryCourseAndStoreCardParams() {
        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(1589905661084430337L);
        dto.setName("Java 后端工程师体系课");
        dto.setPrice(19900);
        dto.setValidDuration(12);
        dto.setUsePeople("零基础学习者");
        dto.setDetail("覆盖 Java 基础、Spring Boot 和项目实战");
        when(courseClient.baseInfo(1589905661084430337L, true)).thenReturn(dto);

        CourseTools tools = new CourseTools(courseClient);
        CourseInfo result = tools.queryCourseById(
                1589905661084430337L,
                new ToolContext(Map.of(Constant.REQUEST_ID, "request-1"))
        );

        assertThat(result.getName()).isEqualTo("Java 后端工程师体系课");
        assertThat(result.getPrice()).isEqualTo(199.0);
        assertThat(ToolResultHolder.get("request-1", "courseInfo_1589905661084430337")).isEqualTo(result);
        verify(courseClient).baseInfo(1589905661084430337L, true);
    }

    @Test
    void shouldReturnNullWhenCourseIdIsMissing() {
        CourseTools tools = new CourseTools(courseClient);

        CourseInfo result = tools.queryCourseById(null, new ToolContext(Map.of(Constant.REQUEST_ID, "request-1")));

        assertThat(result).isNull();
        assertThat(ToolResultHolder.get("request-1")).isNull();
        verifyNoInteractions(courseClient);
    }
}
