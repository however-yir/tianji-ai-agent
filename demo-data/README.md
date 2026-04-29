# Demo Data

本目录为 `dev-demo` profile 提供静态演示数据，确保在无外部微服务和真实数据库时仍可完整展示课程业务 Agent 的 5 条演示链路。

## 文件说明

- `fixtures.json`：课程列表（3 门课）和订单样例（1 笔预下单），供 `CourseTools` 和 `OrderTools` 在 `dev-demo` 模式下使用
- 在 `application-dev-demo.yml` 中通过 `course.fixtures.path` 指向 `demo-data/fixtures.json`

## 覆盖的演示问题

| 序号 | 场景 | 演示问题 | 依赖数据 | 预期路由 | 前端看点 |
|---|---|---|---|---|---|
| 1 | 课程推荐 | `我零基础，想 3 个月入门 Java 后端，帮我推荐课程` | `courses[0]`, `courses[1]` | `RECOMMEND` | 流式推荐说明、课程卡片 |
| 2 | 课程详情 | `介绍一下 1589905661084430337 这门课适合谁，价格多少` | `courses[0].detail` | `CONSULT` | 课程名称、价格、适用人群 |
| 3 | 预下单 | `我要购买课程 1589905661084430337，帮我生成确认订单` | `orders[0]` | `BUY` | 订单金额、优惠、实付、课程 ID |
| 4 | 知识问答 | `Java 中 Redis 缓存穿透是什么，怎么处理` | 不依赖业务数据 | `KNOWLEDGE` | 流式知识回答 |
| 5 | 语音/多模态 | `上传一张课程截图，或用语音问"这门课适合我吗"` | 不依赖业务数据 | `CONSULT` 或 `KNOWLEDGE` | 附件引用、语音入口 |

## 扩展

- 需要更多课程样例时，向 `fixtures.json` 的 `courses` 数组追加条目即可
- 需要模拟更多订单状态（已支付、已取消）时，扩展 `orders` 数组
- 生产环境应替换为真实微服务调用，本目录仅用于本地演示和 CI smoke test
