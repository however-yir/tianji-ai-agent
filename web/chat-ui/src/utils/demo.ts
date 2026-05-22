import type { AttachmentItem, ChatMessage, DemoStore } from "../types";
import { createId, createDemoMessage, stripMarkdown } from "./helpers";

export function createSeedDemoStore(): DemoStore {
  const sessionAId = createId("demo-session");
  const sessionBId = createId("demo-session");
  return {
    sessions: [
      {
        id: sessionAId,
        title: "课程顾问工作台演示",
        updatedAt: new Date(Date.now() - 1000 * 60 * 38).toISOString(),
        messages: [
          createDemoMessage("user", "帮我总结一下 AI 课程咨询助手应该覆盖哪些关键流程？"),
          createDemoMessage(
            "assistant",
            [
              "可以从 4 个环节组织：",
              "",
              "1. 线索进入：识别用户画像、渠道来源、咨询意图",
              "2. 需求诊断：提问学习目标、基础水平、预算和时间",
              "3. 方案推荐：匹配课程、师资、服务组合和风险提示",
              "4. 转化跟进：记录异议、输出待办、沉淀会话纪要",
              "",
              "```mermaid",
              "flowchart LR",
              'A["线索进入"] --> B["需求诊断"]',
              'B --> C["方案推荐"]',
              'C --> D["转化跟进"]',
              "```",
            ].join("\n"),
            {
              params: {
                mode: "demo",
                workflow: "course-consulting",
                leadScore: 86,
              },
              toolSummary: [
                { label: "workflow", value: "course-consulting" },
                { label: "leadScore", value: "86" },
              ],
              references: [
                {
                  title: "演示说明",
                  excerpt: "这条回复使用本地演示数据，便于零配置体验完整前端。",
                  tag: "demo",
                },
              ],
            },
          ),
        ],
      },
      {
        id: sessionBId,
        title: "前端升级建议",
        updatedAt: new Date(Date.now() - 1000 * 60 * 14).toISOString(),
        messages: [
          createDemoMessage("user", "给我一个把聊天前端升级到更专业水准的路线图。"),
          createDemoMessage(
            "assistant",
            [
              "可以按三个阶段推进：",
              "",
              "- 阶段 1：补默认演示模式、登录闭环、错误提示与历史会话",
              "- 阶段 2：补 Markdown、代码高亮、附件、重试与导出",
              "- 阶段 3：补测试、Docker、本地 mock、性能优化",
              "",
              "如果要估算迭代优先级，可以先做公式与图表展示，例如 $F(x)=\\sum_{i=1}^{n}x_i$ 这类输出会更完整。",
            ].join("\n"),
            {
              params: {
                mode: "demo",
                priority: ["体验", "工程化", "演示能力"],
                eta: "7 days",
              },
              references: [
                {
                  title: "项目建议",
                  excerpt: "优先做用户一进入就能跑通的路径，再叠加高级能力。",
                  tag: "strategy",
                },
              ],
            },
          ),
        ],
      },
    ],
  };
}

export function buildDemoReply(question: string, attachments: AttachmentItem[]): Partial<ChatMessage> {
  const normalized = question.toLowerCase();
  const attachmentNames = attachments.map((item) => item.name).join("、");

  if (normalized.includes("推荐课程") || normalized.includes("入门 java") || normalized.includes("java 后端")) {
    return {
      content: [
        "我会先按“目标、基础、周期、转化动作”来推荐课程。",
        "",
        "你是零基础并希望 3 个月入门 Java 后端，优先建议从 Java 基础、Spring Boot 实战、项目就业课三段走。",
        "",
        "推荐顺序：",
        "",
        "1. Java 开发零基础入门：补语法、面向对象和集合基础",
        "2. Spring Boot 企业项目课：把接口、数据库、Redis 串起来",
        "3. Java 后端就业项目课：用完整项目沉淀简历可讲的业务经验",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "RECOMMEND",
        courseInfo_1589905661084430337: {
          id: "1589905661084430337",
          name: "Java 后端工程师体系课",
          price: 199,
          validDuration: 12,
          usePeople: "零基础或转行学习者",
          detail: "覆盖 Java 基础、Spring Boot、Redis、项目实战和面试表达。",
        },
      },
      routeResult: {
        intent: "RECOMMEND",
        confidence: 0.92,
        routeReason: "用户给出目标、基础和周期，适合进入课程推荐",
        nextAgent: "RECOMMEND",
        candidateAgents: ["RECOMMEND", "STUDY_PLAN", "CONSULT"],
        needRag: true,
        needMemory: true,
        riskLevel: "LOW",
      },
      references: [
        {
          title: "RouteAgent 命中推荐场景",
          excerpt: "推荐类问题会进入 RecommendAgent，再由 CourseTools 返回课程卡片参数。",
          tag: "RECOMMEND",
        },
      ],
    };
  }

  if (normalized.includes("适合谁") || normalized.includes("价格多少") || normalized.includes("课程详情")) {
    return {
      content: [
        "这门课更适合希望系统学习 Java 后端、并需要项目经验沉淀的同学。",
        "",
        "课程会覆盖基础语法、Spring Boot、Redis、接口设计和业务项目实践。当前演示价格为 199 元，有效期 12 个月。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "CONSULT",
        courseInfo_1589905661084430337: {
          id: "1589905661084430337",
          name: "Java 后端工程师体系课",
          price: 199,
          validDuration: 12,
          usePeople: "零基础、转行学习者、需要补项目经验的后端初学者",
          detail: "以课程详情查询为核心，适合展示 ConsultAgent + CourseTools 链路。",
        },
      },
      routeResult: {
        intent: "CONSULT",
        confidence: 0.89,
        routeReason: "用户询问课程适用人群和价格，进入课程咨询",
        nextAgent: "CONSULT",
        candidateAgents: ["CONSULT", "RECOMMEND", "BUY"],
        needRag: true,
        needMemory: false,
        riskLevel: "LOW",
      },
    };
  }

  if (normalized.includes("购买课程") || normalized.includes("生成确认订单") || normalized.includes("预下单")) {
    return {
      content: [
        "我已经为你生成一张预下单确认卡片。",
        "",
        "在真实链路中，BuyAgent 不会直接完成支付，而是调用 OrderTools 生成订单确认信息，再由前端展示给用户做最终确认。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "BUY",
        prePlaceOrder: {
          count: 1,
          totalAmount: 199,
          discountAmount: 20,
          couponName: "单券：【新人立减 20 元】",
          payAmount: 179,
          courseIds: ["1589905661084430337"],
          orderId: 202604290001,
          couponId: 9001,
          businessState: "USER_CONFIRM_REQUIRED",
          stateTrace: ["COURSE_SELECTED", "ORDER_PREVIEW", "USER_CONFIRM_REQUIRED"],
          nextAction: "等待用户确认订单；支付异常或敏感问题进入 HANDOFF_OR_DONE",
        },
      },
      routeResult: {
        intent: "BUY",
        confidence: 0.91,
        routeReason: "用户明确要求购买并生成确认订单，进入预下单状态机",
        nextAgent: "BUY",
        candidateAgents: ["BUY", "CONSULT", "HUMAN_HANDOFF"],
        needRag: false,
        needMemory: true,
        riskLevel: "LOW",
      },
      references: [
        {
          title: "Tool Calling",
          excerpt: "OrderTools.prePlaceOrder 返回结构化订单参数，前端按 PARAM 事件渲染卡片。",
          tag: "BUY",
        },
      ],
    };
  }

  if (normalized.includes("缓存穿透") || normalized.includes("redis")) {
    return {
      content: [
        "Redis 缓存穿透指的是：请求查询一个数据库中也不存在的数据，导致每次都绕过缓存打到数据库。",
        "",
        "常见处理方式：",
        "",
        "1. 缓存空值，并设置较短 TTL",
        "2. 使用布隆过滤器提前拦截不存在的 key",
        "3. 对异常流量做限流和参数校验",
        "",
        "在课程业务里，课程 ID 查询就适合先做参数校验，再结合空值缓存保护课程服务。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "KNOWLEDGE",
        topic: "Redis 缓存穿透",
      },
      routeResult: {
        intent: "KNOWLEDGE",
        confidence: 0.88,
        routeReason: "用户询问通用技术概念，进入知识问答",
        nextAgent: "KNOWLEDGE",
        candidateAgents: ["KNOWLEDGE", "CONSULT", "RECOMMEND"],
        needRag: true,
        needMemory: false,
        riskLevel: "LOW",
      },
    };
  }

  if (normalized.includes("mermaid") || normalized.includes("流程")) {
    return {
      content: [
        "下面给你一个更适合汇报的流程梳理：",
        "",
        "```mermaid",
        "flowchart TD",
        'U["用户提问"] --> R["意图识别"]',
        'R --> P["方案规划"]',
        'P --> T["工具调用 / 参数输出"]',
        'T --> A["流式回答"]',
        "```",
        "",
        "这样做的好处是：结构清晰、可演示、后续也容易接自动化测试。",
      ].join("\n"),
      params: {
        mode: "demo",
        scene: "mermaid",
        attachmentCount: attachments.length,
      },
      references: [
        {
          title: "演示模式说明",
          excerpt: "本地演示会模拟 SSE、参数回传和工具调用展示。",
          tag: "demo",
        },
      ],
    };
  }

  if (normalized.includes("公式") || normalized.includes("math")) {
    return {
      content: [
        "可以，下面给你一个带数学公式的示例：",
        "",
        "当我们用一个简单评分来表示会话质量时，可以写成：",
        "",
        "$$Score = 0.45 \\times Intent + 0.35 \\times Context + 0.20 \\times Action$$",
        "",
        "这样就能把意图识别、上下文完整度和行动建议统一进一个评分体系。",
      ].join("\n"),
      params: {
        mode: "demo",
        scene: "math",
      },
    };
  }

  if (attachments.length) {
    return {
      content: [
        `我已经接收到 ${attachments.length} 个附件的上下文信息：${attachmentNames}。`,
        "",
        "当前前端会把附件名、类型和大小一并加入提问上下文，适合演示“基于附件提问”的交互流程。",
        "",
        "如果你后续想做成真正的文件问答，下一步建议补一个后端上传解析接口，把文档内容或图像 OCR 结果再送入模型。",
      ].join("\n"),
      params: {
        mode: "demo",
        attachmentNames,
        attachmentCount: attachments.length,
      },
      references: [
        {
          title: "附件处理建议",
          excerpt: "当前是前端演示版上下文注入，后续可升级为真实上传与解析。",
          tag: "attachment",
        },
      ],
    };
  }

  if (normalized.includes("测试") || normalized.includes("运行")) {
    return {
      content: [
        "如果目标是“更容易跑起来”，建议优先完成这三件事：",
        "",
        "1. 默认进入 `演示模式`，先保证零配置能体验",
        "2. 提供 `真实 API` 连接面板，明确 Token 和依赖说明",
        "3. 用 `docker-compose` 把 MySQL、Redis、后端、前端串起来",
        "",
        "这样对面试、汇报和团队协作都会友好很多。",
      ].join("\n"),
      params: {
        mode: "demo",
        focus: ["demo", "api", "compose"],
      },
    };
  }

  return {
    content: [
      "这是一个更接近成品的演示回复：",
      "",
      `- 你当前的问题是：${stripMarkdown(question) || "未提供具体问题"}`,
      "- 当前运行模式：本地演示",
      "- 支持会话管理、消息重试、Mermaid、数学公式、附件上下文和语音输入",
      "",
      "如果你切到真实 API 模式，界面会继续走同一套工作流，只是数据改成真实后端返回。",
    ].join("\n"),
    params: {
      mode: "demo",
      confidence: "high",
    },
    references: [
      {
        title: "下一步建议",
        excerpt: "可以继续补 Docker、本地 mock、前端自动化测试和真正的文件上传接口。",
        tag: "next",
      },
    ],
  };
}
