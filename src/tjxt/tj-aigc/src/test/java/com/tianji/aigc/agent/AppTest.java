package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.app.ApplicationResult;
import com.alibaba.dashscope.utils.JsonUtils;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("manual-integration")
public class AppTest {

    @Test
    public void testAppCall() throws Exception {
        // 构造业务参数
        String token = requireEnv("BAILIAN_USER_TOKEN");
        String apiKey = requireEnv("ALIYUN_API_KEY");
        Map<String, Object> bizParams = MapUtil.<String, Object>builder()
                .put("user_defined_tokens", MapUtil.of("tool_d7628c97-92ac-435c-82fd-2436358c9915", // 工具id
                        MapUtil.of("user_token", token)))
                .build();

        // bizParams.add("user_defined_tokens", JsonObject);
        ApplicationParam param = ApplicationParam.builder()
                // 若没有配置环境变量，可用百炼API Key将下行替换为：.apiKey("sk-xxx")。但不建议在生产环境中直接将API Key硬编码到代码中，以减少API Key泄露风险。
                .apiKey(apiKey)
                .appId("0e1bcb0cc6544c438519afee7a518155") // 智能体id
                .prompt("查询课程，id为：1880533253575225346")
                .incrementalOutput(true) // 开启增量输出
                .bizParams(JsonUtils.toJsonObject(bizParams))
                .build();

        Application application = new Application();
        Flowable<ApplicationResult> result = application.streamCall(param);

        // 阻塞式的打印内容
        result.blockingForEach(data -> {
            System.out.printf("%s\n",data.getOutput().getText());
        });

    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 " + key);
        }
        return value;
    }

}
