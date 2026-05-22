# Multi-Agent Evaluation Checklist

## 1. 任务拆解
- [ ] Planner 能把复杂任务拆成可执行子任务
- [ ] 子任务之间依赖关系正确
- [ ] 子任务失败可回滚或重试

## 2. 路由与工具调用
- [x] RouteAgent 路由准确率 >= 85%（见 `route-agent-dataset.jsonl`，可用 `scripts/evaluation/evaluate_route_agent.py` 或 `scripts/evaluation/evaluate_route_predictions.py` 复现）
- [ ] 工具调用成功率 >= 95%
- [ ] 工具失败时可自动降级

```bash
python3 scripts/evaluation/evaluate_route_predictions.py \
  --dataset docs/evaluation/route-agent-dataset.jsonl \
  --predictions docs/evaluation/route-agent-predictions.sample.jsonl \
  --min-accuracy 0.85
```

## 3. 状态与可观测性
- [ ] 每个任务具备 `task_id`
- [ ] 每步执行具备 `agent_id` + `trace_id`
- [ ] 失败案例可追踪到输入、决策、输出

## 4. 结果质量
- [ ] 多步任务完成率 >= 80%
- [ ] 关键路径响应时间满足目标
- [ ] 输出结果具备可解释性

## 5. 回归验证
- [ ] 增加标准评测样本（当前 RouteAgent JSONL 24 条，后续扩到至少 50 条）
- [ ] nightly 回归执行
- [ ] 回归结果按版本归档
