# tianji-ai-agent Resume Upgrade Checklist

## 1. 功能
- [x] 多智能体基础角色（Route/Consult/Knowledge/Recommend/Buy）
- [ ] 任务拆解器（Planner）标准化
- [ ] 任务状态机（created/running/retry/success/failed）
- [ ] 失败重试与兜底策略统一

## 2. 工程化
- [x] 新增 CI（核心模块编译）
- [x] 新增 `.env.example`
- [x] 新增多智能体评测清单
- [ ] OpenTelemetry + Prometheus 指标
- [ ] 结构化日志串联 `request_id/task_id/trace_id/agent_id`
- [ ] Docker / docker-compose 统一启动

## 3. README
- [x] 增加改造清单入口
- [ ] 多 Agent 架构图
- [ ] 任务执行时序图
- [ ] 真实任务案例对比（单 Agent vs 多 Agent）

## 4. 测试
- [x] 评测数据模板生成脚本
- [ ] Agent 路由测试
- [ ] 工具调用成功/失败测试
- [ ] 状态流转与重试测试
- [ ] 集成回归测试
