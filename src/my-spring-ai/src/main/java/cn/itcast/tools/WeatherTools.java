package cn.itcast.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.itcast.dto.WeatherDTO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

    @Tool(description = "根据城市id查询天气信息")
    public WeatherDTO getWeather(@ToolParam(description = "城市id") String cityId){
        // cityId 直接拼进 URL，必须是纯数字，防止路径注入/SSRF；并给 HTTP 请求设置超时
        if (cityId == null || !cityId.matches("\\d{1,9}")) {
            throw new IllegalArgumentException("城市id必须是1-9位数字");
        }
        String url = "http://t.weather.itboy.net/api/weather/city/" + cityId;
        String jsonData = HttpUtil.get(url, 5000);
        JSONObject jsonObject = JSONUtil.parseObj(jsonData);
        // 模拟返回天气信息
        return WeatherDTO.builder()
                .cityId(jsonObject.getByPath("cityInfo.citykey", String.class)) // 城市ID
                .city(jsonObject.getByPath("cityInfo.city", String.class)) // 城市名称
                .temperature(jsonObject.getByPath("data.wendu", String.class))   // 当前温度
                .lowTemperature(jsonObject.getByPath("data.forecast[0].low", String.class))// 低温
                .highTemperature(jsonObject.getByPath("data.forecast[0].high", String.class))// 高温
                .date(jsonObject.getByPath("date", String.class))// 数据日期
                .quality(jsonObject.getByPath("data.quality", String.class))// 空气质量
                .pm25(jsonObject.getByPath("data.pm25", Double.class))// PM2.5数值
                .build();
    }

}
