package com.tianji.aigc.tools;

import cn.hutool.core.convert.Convert;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId,
                                      ToolContext toolContext
    ) {
        return Optional.ofNullable(courseId)
                .map(id -> CourseInfo.of(this.courseClient.baseInfo(courseId, true)))
                .map(courseInfo -> {
                    String key = Convert.toStr(toolContext.getContext().get(Constant.REQUEST_ID));
                    String field = "courseInfo_" + courseId;
                    // 数据存入到容器
                    ToolResultHolder.put(key, field, courseInfo);
                    return courseInfo;
                })
                .orElse(null);
    }

}
