package cn.itcast.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class ReactorTest {

    @Test
    public void test() {
        // 创建 Flux
        Flux<String> flux = Flux.just("苹果", "香蕉", "葡萄", "面条") // 创建一个包含四个元素的 Flux
                .filter(s -> !s.equals("面条")) // 过滤掉面条元素
                .doFirst(() -> System.out.println("开始处理......")) // 在处理开始时打印信息
                .doOnNext(s -> System.out.println("当前元素: " + s)) // 在每个元素被处理后打印信息
                .map(s -> { // 每个元素进行数据处理
                    return switch (s) {
                        case "苹果" -> "苹果是红色的";
                        case "香蕉" -> "香蕉是黄色的";
                        case "葡萄" -> "葡萄是紫色的";
                        default -> "未知水果";
                    };
                })
                .doOnComplete(() -> System.out.println("处理完成......")) // 在完成时打印信息
                .concatWith(Flux.just("[END]")) // 在最后添加一个元素
                ;

        // 订阅消费
        flux.subscribe(s -> System.out.println("消费者: " + s));
    }
}
