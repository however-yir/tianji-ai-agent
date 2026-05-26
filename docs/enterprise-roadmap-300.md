# 企业级完善路线图：300 条建议

## 使用方式

这 300 条建议按企业上线成熟度拆分为可执行 backlog。标记含义：

- `Repo`：可直接在本仓库推进。
- `Infra`：依赖云资源、Kubernetes、数据库、网络或监控平台。
- `Biz`：依赖课程、交易、客服、运营等业务系统。
- `Gov`：依赖安全、合规、审批或组织流程。

## 建议清单

### 架构与边界

- [ ] E001 [Repo] 明确 `tj-aigc` 只负责 Agent 编排，不承载课程和交易核心状态。
- [ ] E002 [Repo] 为 AgentHarness 增加架构决策记录。
- [ ] E003 [Repo] 固化 `RouteAgent -> 子Agent -> AgentHarness -> Runtime -> Observation -> SSE` 链路图。
- [ ] E004 [Repo] 为每个子 Agent 建立职责说明。
- [ ] E005 [Repo] 为每个工具定义输入、输出、失败语义和审计字段。
- [ ] E006 [Repo] 将 `mcp.call` 保持为默认拒绝的扩展位。
- [ ] E007 [Repo] 明确同步工具和异步任务的边界。
- [ ] E008 [Repo] 为附件解析链路建立单独边界文档。
- [ ] E009 [Repo] 把业务动作 schema 与 Spring AI Tool 描述解耦。
- [ ] E010 [Repo] 为 AgentRuntime 建立适配层命名规范。
- [ ] E011 [Repo] 为前端 SSE 事件建立版本化契约。
- [ ] E012 [Repo] 记录课程、订单、KnowledgeOps 依赖的 SLA 假设。
- [ ] E013 [Repo] 定义降级回答和转人工的职责边界。
- [ ] E014 [Repo] 明确演示模式与生产模式差异。
- [ ] E015 [Repo] 建立模块所有者表。

### Agent 治理

- [ ] E016 [Repo] 为每个 `ActionSchema` 增加 owner。
- [ ] E017 [Repo] 为每个动作增加风险等级。
- [ ] E018 [Repo] 为 `ActionPolicyGuard` 增加拒绝原因枚举。
- [ ] E019 [Repo] 对未知 `order.*` 动作保持默认拒绝。
- [ ] E020 [Repo] 为 `order.preview` 增加支付字段黑名单测试。
- [ ] E021 [Repo] 为 `course.search` 增加关键字长度限制。
- [ ] E022 [Repo] 为 `knowledgeops.rag_query` 增加租户隔离字段。
- [ ] E023 [Repo] 为 MCP 工具增加 allowlist 设计文档。
- [ ] E024 [Repo] 将 policy decision 写入结构化 TRACE。
- [ ] E025 [Repo] 为 policy 拒绝建立前端展示样式。
- [ ] E026 [Repo] 增加人工转接的统一 observation。
- [ ] E027 [Repo] 为敏感意图建立统一分类器测试集。
- [ ] E028 [Gov] 建立高风险工具变更审批。
- [ ] E029 [Gov] 建立提示词变更 review 流程。
- [ ] E030 [Gov] 建立 Agent 行为事故复盘模板。

### 安全与合规

- [ ] E031 [Repo] 建立日志脱敏单元测试。
- [ ] E032 [Repo] 对 API key、token、密码增加 secret scan 样例。
- [ ] E033 [Repo] 明确 TRACE 中禁止输出的字段。
- [ ] E034 [Repo] 增加附件魔数校验回归测试。
- [ ] E035 [Repo] 对上传大小和解析耗时建立配置项。
- [ ] E036 [Repo] 增加 CORS 生产配置说明。
- [ ] E037 [Repo] 增加安全响应头测试。
- [ ] E038 [Repo] 建立登录态缺失时的错误契约。
- [ ] E039 [Repo] 建立租户 ID 传递规范。
- [ ] E040 [Repo] 建立越权课程查询的测试样例。
- [ ] E041 [Gov] 完成数据分级分类。
- [ ] E042 [Gov] 完成隐私影响评估。
- [ ] E043 [Gov] 完成模型供应商安全评估。
- [ ] E044 [Gov] 建立生产访问审批。
- [ ] E045 [Gov] 建立审计日志留存策略。

### 身份、权限与租户

- [ ] E046 [Repo] 把用户 ID、租户 ID、session ID 统一进上下文对象。
- [ ] E047 [Repo] 为 ToolContext 缺失用户信息建立拒绝策略。
- [ ] E048 [Repo] 为不同租户配置工具 allowlist。
- [ ] E049 [Repo] 为租户级模型配置建立 schema。
- [ ] E050 [Repo] 为租户级限流建立接口。
- [ ] E051 [Repo] 为租户级知识库隔离建立测试。
- [ ] E052 [Repo] 为租户级课程可见性建立测试。
- [ ] E053 [Repo] 为租户级订单预览权限建立测试。
- [ ] E054 [Infra] 接入企业 SSO。
- [ ] E055 [Infra] 接入统一 IAM。
- [ ] E056 [Infra] 配置生产 RBAC。
- [ ] E057 [Infra] 配置 Kubernetes service account 最小权限。
- [ ] E058 [Gov] 建立租户开通审批。
- [ ] E059 [Gov] 建立权限变更审计。
- [ ] E060 [Gov] 建立离职账号回收流程。

### 数据与存储

- [ ] E061 [Repo] 明确 MySQL schema 迁移策略。
- [ ] E062 [Repo] 为 Flyway 失败增加排障文档。
- [ ] E063 [Repo] 为 Redis key 设计建立命名规范。
- [ ] E064 [Repo] 为会话记忆 TTL 增加配置说明。
- [ ] E065 [Repo] 为附件存储建立生命周期策略文档。
- [ ] E066 [Repo] 为聊天历史分页建立性能测试。
- [ ] E067 [Repo] 为消息幂等写入建立设计。
- [ ] E068 [Repo] 为 ToolResultHolder 建立容量上限。
- [ ] E069 [Infra] 配置 MySQL 自动备份。
- [ ] E070 [Infra] 配置 MySQL PITR。
- [ ] E071 [Infra] 配置 Redis 高可用。
- [ ] E072 [Infra] 配置对象存储。
- [ ] E073 [Infra] 配置数据库慢查询告警。
- [ ] E074 [Gov] 建立数据保留周期。
- [ ] E075 [Gov] 建立数据删除请求流程。

### CI/CD 与发布

- [ ] E076 [Repo] 保持 `main` 阻塞 CI 覆盖前端、Python、Java。
- [ ] E077 [Repo] 将企业上线校验脚本接入 CI。
- [ ] E078 [Repo] 为 Helm 模板增加 lint。
- [ ] E079 [Repo] 为 Dockerfile 增加构建测试。
- [ ] E080 [Repo] 固定 Node、Java、Python 版本。
- [ ] E081 [Repo] 为发布清单增加 CI 检查。
- [ ] E082 [Repo] 为 changelog 增加发布模板。
- [ ] E083 [Repo] 为 release tag 建立命名规范。
- [ ] E084 [Repo] 为 GitHub Actions artifact 建立保留策略。
- [ ] E085 [Infra] 启用镜像签名。
- [ ] E086 [Infra] 启用 SBOM 生成。
- [ ] E087 [Infra] 启用镜像漏洞阻断策略。
- [ ] E088 [Infra] 建立 staging 自动部署。
- [ ] E089 [Infra] 建立 canary 发布流水线。
- [ ] E090 [Gov] 建立生产发布审批。

### 测试体系

- [ ] E091 [Repo] 增加 RouteAgent 离线评测集覆盖。
- [ ] E092 [Repo] 增加 BuyAgent 状态机测试。
- [ ] E093 [Repo] 增加 CourseTools 异常测试。
- [ ] E094 [Repo] 增加 OrderTools policy 拒绝测试。
- [ ] E095 [Repo] 增加 KnowledgeOps 超时测试。
- [ ] E096 [Repo] 增加 SSE 事件顺序测试。
- [ ] E097 [Repo] 增加前端 TRACE 渲染测试。
- [ ] E098 [Repo] 增加前端 PARAM 卡片测试。
- [ ] E099 [Repo] 增加附件上传安全测试。
- [ ] E100 [Repo] 增加 Redis 不可用降级测试。
- [ ] E101 [Repo] 增加模型失败降级测试。
- [ ] E102 [Repo] 增加人工转接路由测试。
- [ ] E103 [Repo] 增加回归测试数据快照。
- [ ] E104 [Infra] 建立 staging E2E。
- [ ] E105 [Infra] 建立压测流水线。

### 可观测性

- [ ] E106 [Repo] 为 Agent 动作增加 Micrometer counter。
- [ ] E107 [Repo] 为工具调用 latency 增加 timer。
- [ ] E108 [Repo] 为 policy 拒绝增加指标。
- [ ] E109 [Repo] 为 SSE 中断增加指标。
- [ ] E110 [Repo] 为人工转接率增加指标。
- [ ] E111 [Repo] 为模型 token 用量增加指标。
- [ ] E112 [Repo] 为 KnowledgeOps 命中率增加指标。
- [ ] E113 [Repo] 为 RouteAgent 置信度分布增加指标。
- [ ] E114 [Repo] 为前端错误增加上报入口。
- [ ] E115 [Repo] 为日志增加 requestId 和 sessionId。
- [ ] E116 [Infra] 配置 Prometheus scrape。
- [ ] E117 [Infra] 配置 Grafana dashboard。
- [ ] E118 [Infra] 配置日志平台索引。
- [ ] E119 [Infra] 配置告警渠道。
- [ ] E120 [Gov] 建立 SLO 月报。

### 稳定性与韧性

- [ ] E121 [Repo] 为课程服务调用增加超时配置。
- [ ] E122 [Repo] 为订单服务调用增加超时配置。
- [ ] E123 [Repo] 为 KnowledgeOps 调用增加超时配置。
- [ ] E124 [Repo] 为模型调用增加 circuit breaker。
- [ ] E125 [Repo] 为工具调用增加失败分类。
- [ ] E126 [Repo] 为幂等请求增加 request key 设计。
- [ ] E127 [Repo] 为 SSE 背压增加测试。
- [ ] E128 [Repo] 为超长回答增加截断策略。
- [ ] E129 [Repo] 为附件解析增加隔离线程池。
- [ ] E130 [Repo] 为高风险链路增加兜底回复。
- [ ] E131 [Infra] 配置 HPA。
- [ ] E132 [Infra] 配置 PDB。
- [ ] E133 [Infra] 配置节点亲和与反亲和。
- [ ] E134 [Infra] 配置多可用区。
- [ ] E135 [Infra] 建立故障演练。

### 性能与容量

- [ ] E136 [Repo] 建立聊天接口基准测试。
- [ ] E137 [Repo] 建立 SSE 并发测试。
- [ ] E138 [Repo] 建立工具调用 P95 预算。
- [ ] E139 [Repo] 建立 RouteAgent 延迟预算。
- [ ] E140 [Repo] 建立前端首屏性能预算。
- [ ] E141 [Repo] 优化前端大 chunk 拆分。
- [ ] E142 [Repo] 建立缓存命中率指标。
- [ ] E143 [Repo] 建立上下文 token 预算。
- [ ] E144 [Repo] 对课程卡片数据做字段裁剪。
- [ ] E145 [Repo] 对历史消息分页做索引检查。
- [ ] E146 [Infra] 建立压测环境。
- [ ] E147 [Infra] 建立容量模型。
- [ ] E148 [Infra] 建立自动扩缩容阈值。
- [ ] E149 [Infra] 建立 CDN 缓存策略。
- [ ] E150 [Biz] 建立高峰期业务降级策略。

### 前端体验

- [ ] E151 [Repo] 为 TRACE 轨迹增加折叠视图。
- [ ] E152 [Repo] 为 policy 拒绝增加友好提示。
- [ ] E153 [Repo] 为课程卡片增加加载和失败状态。
- [ ] E154 [Repo] 为订单预览增加人工确认提示。
- [ ] E155 [Repo] 为停止生成增加状态回显。
- [ ] E156 [Repo] 为附件上传增加进度。
- [ ] E157 [Repo] 为语音入口增加错误提示。
- [ ] E158 [Repo] 为网络中断增加重试按钮。
- [ ] E159 [Repo] 为移动端布局增加截图回归。
- [ ] E160 [Repo] 为可访问性增加键盘测试。
- [ ] E161 [Repo] 为深色模式建立视觉规范。
- [ ] E162 [Repo] 为长回答增加目录或折叠。
- [ ] E163 [Repo] 为引用来源增加可信度展示。
- [ ] E164 [Repo] 为人工客服入口增加固定位置。
- [ ] E165 [Biz] 对客服话术做业务验收。

### 业务闭环

- [ ] E166 [Biz] 明确课程推荐排序规则。
- [ ] E167 [Biz] 明确课程详情可展示字段。
- [ ] E168 [Biz] 明确课程价格实时性要求。
- [ ] E169 [Biz] 明确优惠、库存、有效期展示规则。
- [ ] E170 [Biz] 明确预下单后用户确认流程。
- [ ] E171 [Biz] 明确支付必须跳转到受控交易系统。
- [ ] E172 [Biz] 明确退款和售后只转人工或官方流程。
- [ ] E173 [Biz] 明确投诉分类和升级路径。
- [ ] E174 [Biz] 明确运营推荐位和 Agent 推荐的优先级。
- [ ] E175 [Biz] 明确课程下架后的回答策略。
- [ ] E176 [Biz] 建立课程知识库更新机制。
- [ ] E177 [Biz] 建立推荐效果评估指标。
- [ ] E178 [Biz] 建立订单预览转化率指标。
- [ ] E179 [Biz] 建立人工转接满意度指标。
- [ ] E180 [Biz] 建立客服质检抽样流程。

### KnowledgeOps 与 RAG

- [ ] E181 [Repo] 为 RAG 查询记录 query、topK、命中文档。
- [ ] E182 [Repo] 为引用来源建立统一前端模型。
- [ ] E183 [Repo] 为无命中场景建立降级回答。
- [ ] E184 [Repo] 为低可信来源增加拒答策略。
- [ ] E185 [Repo] 为知识库版本写入 TRACE。
- [ ] E186 [Repo] 为 RAG 超时增加 fallback。
- [ ] E187 [Repo] 为 RAG 结果增加租户过滤。
- [ ] E188 [Repo] 为 RAG prompt 注入防护增加测试。
- [ ] E189 [Infra] 建立向量库备份。
- [ ] E190 [Infra] 建立知识索引重建流水线。
- [ ] E191 [Biz] 建立知识入库审核。
- [ ] E192 [Biz] 建立知识过期提醒。
- [ ] E193 [Biz] 建立课程 FAQ 维护机制。
- [ ] E194 [Gov] 建立知识来源授权记录。
- [ ] E195 [Gov] 建立错误回答纠偏流程。

### MCP 扩展

- [ ] E196 [Repo] 为 `mcp.call` 设计 tool registry。
- [ ] E197 [Repo] 为 MCP server 增加权限模型。
- [ ] E198 [Repo] 为 MCP call 增加超时和取消。
- [ ] E199 [Repo] 为 MCP call 增加审计字段。
- [ ] E200 [Repo] 为 MCP 工具响应增加 schema 校验。
- [ ] E201 [Repo] 为 MCP 工具失败增加隔离策略。
- [ ] E202 [Repo] 为 MCP 工具 allowlist 增加测试。
- [ ] E203 [Repo] 为外部浏览器类 MCP 默认禁用。
- [ ] E204 [Repo] 为写操作 MCP 增加人工确认。
- [ ] E205 [Repo] 为 MCP 工具成本增加指标。
- [ ] E206 [Infra] 部署受控 MCP 网关。
- [ ] E207 [Infra] 配置 MCP 服务发现。
- [ ] E208 [Infra] 配置 MCP 网络隔离。
- [ ] E209 [Gov] 建立 MCP 工具准入审批。
- [ ] E210 [Gov] 建立 MCP 工具下线机制。

### 模型与提示词

- [ ] E211 [Repo] 为 RouteAgent prompt 建立版本号。
- [ ] E212 [Repo] 为 BuyAgent prompt 建立风险测试。
- [ ] E213 [Repo] 为 RecommendAgent prompt 建立推荐一致性测试。
- [ ] E214 [Repo] 为 KnowledgeAgent prompt 建立引用要求。
- [ ] E215 [Repo] 为提示词变更增加 changelog。
- [ ] E216 [Repo] 为模型参数建立配置说明。
- [ ] E217 [Repo] 为模型切换建立兼容测试。
- [ ] E218 [Repo] 为幻觉高风险问题建立拒答样例。
- [ ] E219 [Repo] 为 jailbreak 样例建立评测集。
- [ ] E220 [Repo] 为多轮上下文漂移建立测试。
- [ ] E221 [Infra] 接入模型调用网关。
- [ ] E222 [Infra] 配置供应商级限流。
- [ ] E223 [Infra] 配置模型成本报表。
- [ ] E224 [Gov] 建立模型版本审批。
- [ ] E225 [Gov] 建立提示词生产发布审批。

### 成本管理

- [ ] E226 [Repo] 为每次会话记录估算 token。
- [ ] E227 [Repo] 为不同 Agent 记录模型成本。
- [ ] E228 [Repo] 为长上下文建立裁剪策略。
- [ ] E229 [Repo] 为重复问题建立缓存策略。
- [ ] E230 [Repo] 为 RAG topK 建立成本上限。
- [ ] E231 [Repo] 为附件解析建立大小上限。
- [ ] E232 [Repo] 为语音转写建立时长上限。
- [ ] E233 [Repo] 为异常重试建立次数上限。
- [ ] E234 [Infra] 建立成本 dashboard。
- [ ] E235 [Infra] 建立每日预算告警。
- [ ] E236 [Infra] 建立租户成本归因。
- [ ] E237 [Biz] 建立高价值用户优先级策略。
- [ ] E238 [Biz] 建立免费额度策略。
- [ ] E239 [Gov] 建立成本复盘机制。
- [ ] E240 [Gov] 建立供应商价格变更评估。

### 运维与支持

- [ ] E241 [Repo] 保持 Runbook 与发布清单同步。
- [ ] E242 [Repo] 为常见错误码建立说明。
- [ ] E243 [Repo] 为健康检查失败建立排障路径。
- [ ] E244 [Repo] 为 GitHub Actions 红灯建立处理流程。
- [ ] E245 [Repo] 为 Helm rollback 建立演练记录。
- [ ] E246 [Repo] 为应用启动失败建立排障清单。
- [ ] E247 [Repo] 为依赖服务不可用建立降级清单。
- [ ] E248 [Repo] 为事故复盘建立模板。
- [ ] E249 [Infra] 建立 on-call 排班。
- [ ] E250 [Infra] 建立告警升级。
- [ ] E251 [Infra] 建立生产只读排障账号。
- [ ] E252 [Infra] 建立日志查询权限。
- [ ] E253 [Biz] 建立客服应急话术。
- [ ] E254 [Biz] 建立运营公告流程。
- [ ] E255 [Gov] 建立 SLA 与支持边界。

### 文档与交付

- [ ] E256 [Repo] 保持 README 与真实链路一致。
- [ ] E257 [Repo] 保持资源索引清晰区分仓库内外资产。
- [ ] E258 [Repo] 为部署命令提供 staging 和 production 示例。
- [ ] E259 [Repo] 为配置项建立表格。
- [ ] E260 [Repo] 为 API 建立 OpenAPI 导出流程。
- [ ] E261 [Repo] 为前端操作建立演示脚本。
- [ ] E262 [Repo] 为评测结果建立证据目录。
- [ ] E263 [Repo] 为架构变更建立 ADR 目录。
- [ ] E264 [Repo] 为贡献流程建立说明。
- [ ] E265 [Repo] 为本地开发建立故障排查 FAQ。
- [ ] E266 [Repo] 为手工集成测试建立前置条件。
- [ ] E267 [Repo] 为安全限制建立面向业务方说明。
- [ ] E268 [Biz] 建立业务验收脚本。
- [ ] E269 [Biz] 建立培训材料。
- [ ] E270 [Gov] 建立交付签收清单。

### 组织流程

- [ ] E271 [Gov] 指定技术 owner。
- [ ] E272 [Gov] 指定业务 owner。
- [ ] E273 [Gov] 指定安全 owner。
- [ ] E274 [Gov] 指定运维 owner。
- [ ] E275 [Gov] 建立需求优先级机制。
- [ ] E276 [Gov] 建立风险登记册。
- [ ] E277 [Gov] 建立上线评审会。
- [ ] E278 [Gov] 建立变更冻结窗口。
- [ ] E279 [Gov] 建立事故响应小组。
- [ ] E280 [Gov] 建立供应商联系人清单。
- [ ] E281 [Gov] 建立数据处理协议。
- [ ] E282 [Gov] 建立权限定期审计。
- [ ] E283 [Gov] 建立合规证据归档。
- [ ] E284 [Gov] 建立季度架构复盘。
- [ ] E285 [Gov] 建立年度灾备演练。

### 商业化与增长

- [ ] E286 [Biz] 定义 Agent 带来的核心业务 KPI。
- [ ] E287 [Biz] 建立课程推荐转化漏斗。
- [ ] E288 [Biz] 建立预下单转支付漏斗。
- [ ] E289 [Biz] 建立人工客服节省工时指标。
- [ ] E290 [Biz] 建立用户满意度采样。
- [ ] E291 [Biz] 建立 A/B 实验框架。
- [ ] E292 [Biz] 建立推荐策略实验。
- [ ] E293 [Biz] 建立课程冷启动策略。
- [ ] E294 [Biz] 建立用户分群策略。
- [ ] E295 [Biz] 建立留资和跟进流程。
- [ ] E296 [Biz] 建立销售线索同步。
- [ ] E297 [Biz] 建立运营活动接入规范。
- [ ] E298 [Biz] 建立企业客户定制配置。
- [ ] E299 [Biz] 建立客户成功反馈闭环。
- [ ] E300 [Gov] 建立从路线图到季度 OKR 的追踪机制。
