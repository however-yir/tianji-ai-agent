package cn.itcast.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.itcast.dto.WeatherDTO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    @Tool(description = "根据城市id查询天气信息")
    public WeatherDTO getWeather(@ToolParam(description = "城市id") String cityId) {
        // 通过http请求获取天气信息，并且通过json数据解析为WeatherDTO对象
        String url = "http://t.weather.itboy.net/api/weather/city/" + cityId;
        String data = HttpUtil.get(url);
        JSONObject jsonObject = JSONUtil.parseObj(data);

        return WeatherDTO.builder()
                .cityId(jsonObject.getByPath("cityInfo.citykey", String.class)) // 城市ID
                .city(jsonObject.getByPath("cityInfo.city", String.class)) // 城市名称
                .date(jsonObject.getByPath("date", String.class))// 数据日期
                .temperature(jsonObject.getByPath("data.wendu", String.class))   // 当前温度
                .lowTemperature(jsonObject.getByPath("data.forecast[0].low", String.class))// 低温
                .highTemperature(jsonObject.getByPath("data.forecast[0].high", String.class))// 高温
                .quality(jsonObject.getByPath("data.quality", String.class))// 空气质量
                .pm25(jsonObject.getByPath("data.pm25", Double.class))// PM2.5数值
                .build();
    }

}
