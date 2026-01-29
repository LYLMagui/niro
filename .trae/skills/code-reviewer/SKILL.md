---
name: code-reviewer
description: 自动化代码审查，检查代码质量、架构合规性、编码标准，并与规格定义进行比对。
---

# Code Reviewer 技能

## 触发时机

- Pull Request 创建或更新
- 开发人员请求代码审查
- CI/CD Pipeline 质量门槛
- 与 `multi-model-reviewer` 协作时

## 核心任务

1. **架构合规性检查**：验证 Clean Architecture 分层
2. **编码标准检查**：语言特定的编码规范
3. **规格对照**：代码实现是否符合规格定义
4. **测试覆盖**：确认关键路径有测试

---

## 审查维度

### 1. Clean Architecture 合规性

```yaml
architecture_checks:
  layer_dependency:
    name: "依赖方向"
    rule: "外层只能依赖内层，内层不得依赖外层"
    layers:
      - adapter (外) → usecase → entity (内)
    violations:
      - "Entity 不得 import UseCase"
      - "UseCase 不得 import Adapter"
  
  package_structure:
    name: "包结构"
    rule: "符合 {aggregate}/{layer}/{component} 结构"
    expected:
      - "{aggregate}/adapter/in/web/"
      - "{aggregate}/adapter/out/persistence/"
      - "{aggregate}/entity/"
      - "{aggregate}/usecase/port/"
      - "{aggregate}/usecase/service/"
```

### 2. DDD 模式检查

```yaml
ddd_checks:
  aggregate_root:
    name: "Aggregate Root 识别"
    rule: "Aggregate Root 必须控制子实体的生命周期"
    markers:
      - "@AggregateRoot annotation"
      - "private constructor for child entities"
  
  value_object:
    name: "Value Object 不变性"
    rule: "Value Object 必须 immutable"
    checks:
      - "record class 或 final fields"
      - "no setters"
      - "equals/hashCode based on all fields"
  
  domain_event:
    name: "Domain Event 标准"
    rule: "符合 domain-event-standard.yaml"
    checks:
      - "sealed interface DomainEvent"
      - "includes standard metadata"
      - "occurredOn timestamp"
```

### 3. 语言特定标准

参考对应的编码标准：

| 语言 | 参考文件 |
|------|----------|
| Java | `coding-standards/references/JAVA_CLEAN_ARCH.md` |
| TypeScript | `coding-standards/references/TYPESCRIPT.md` |
| Go | `coding-standards/references/GOLANG.md` |
| Rust | `coding-standards/references/RUST.md` |

---

## 审查检查清单

### Use Case Service

```yaml
usecase_checks:
  - id: UC1
    name: "单一职责"
    rule: "一个 Service 只处理一个 Use Case"
    
  - id: UC2
    name: "Port 依赖"
    rule: "通过 Port interface 依赖外部资源"
    
  - id: UC3
    name: "输入验证"
    rule: "Input DTO 在 UseCase 层验证"
    
  - id: UC4
    name: "Domain Event 发布"
    rule: "状态变更后发布对应 Domain Event"
    
  - id: UC5
    name: "事务边界"
    rule: "Aggregate 操作在单一事务内完成"
```

### Aggregate Entity

```yaml
aggregate_checks:
  - id: AG1
    name: "Invariant 保护"
    rule: "所有 public 方法必须维护 invariants"
    
  - id: AG2
    name: "私有构造函数"
    rule: "Child Entity 使用 private/package constructor"
    
  - id: AG3
    name: "状态封装"
    rule: "不直接暴露可变集合"
    
  - id: AG4
    name: "Factory Method"
    rule: "复杂对象使用 Factory 创建"
```

### Repository/Adapter

```yaml
adapter_checks:
  - id: AD1
    name: "Port 实现"
    rule: "Adapter 必须实现对应的 Port interface"
    
  - id: AD2
    name: "依赖注入"
    rule: "通过 Constructor Injection"
    
  - id: AD3
    name: "错误转换"
    rule: "Infrastructure 错误转换为 Domain 错误"
```

---

## 输出格式

### 审查报告

```
╔═══════════════════════════════════════════════════════════════════╗
║                      CODE REVIEW REPORT                            ║
╠═══════════════════════════════════════════════════════════════════╣
║ File: CreateWorkflowService.java                                   ║
║ Aggregate: Workflow                                                ║
║ Layer: usecase/service                                             ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                    ║
║ ✅ UC1: Single Responsibility                    PASS              ║
║ ✅ UC2: Port Dependency                          PASS              ║
║ ✅ UC3: Input Validation                         PASS              ║
║ ⚠️ UC4: Domain Event Publication                 WARNING           ║
║    └─ Event 'WorkflowCreated' missing 'metadata' field            ║
║ ✅ UC5: Transaction Boundary                     PASS              ║
║                                                                    ║
╠═══════════════════════════════════════════════════════════════════╣
║ TOTAL: 4/5 PASS, 1 WARNING                                         ║
╚═══════════════════════════════════════════════════════════════════╝
```

### 问题详情

```yaml
review_issues:
  - id: CR-001
    file: "CreateWorkflowService.java"
    line: 45
    severity: warning
    check: UC4
    message: "Domain Event 'WorkflowCreated' missing 'metadata' field"
    
    current_code: |
      return new WorkflowCreated(
          workflow.getId(),
          workflow.getBoardId(),
          workflow.getName()
      );
    
    suggested_fix: |
      return new WorkflowCreated(
          workflow.getId(),
          workflow.getBoardId(),
          workflow.getName(),
          EventMetadata.now()  // Add metadata
      );
    
    spec_reference: "aggregate.yaml#domain_events.WorkflowCreated"
```

---

## 与其他 Skills 协作

```
                    ┌─────────────────────┐
                    │   code-reviewer     │ ◄── 本 Skill
                    │   (代码审查)         │
                    └──────────┬──────────┘
                               │
           ┌───────────────────┼───────────────────┐
           │                   │                   │
           ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   arch-guard    │ │coding-standards │ │ multi-model-    │
│ (架构守护)       │ │ (编码标准)       │ │ reviewer        │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## 命令行工具

```bash
# 审查单一文件
python ~/.claude/skills/code-reviewer/scripts/review.py \
    --file src/workflow/usecase/service/CreateWorkflowService.java

# 审查目录
python ~/.claude/skills/code-reviewer/scripts/review.py \
    --dir src/workflow/

# 比对规格
python ~/.claude/skills/code-reviewer/scripts/review.py \
    --file src/workflow/usecase/service/CreateWorkflowService.java \
    --spec docs/specs/create-workflow/

# PR 审查模式
python ~/.claude/skills/code-reviewer/scripts/review.py \
    --git-diff origin/main..HEAD
```

---

## 配置文件

### .code-review.yaml

```yaml
language: java
architecture: clean-architecture

checks:
  architecture:
    enabled: true
    strict: true
    
  coding_standards:
    enabled: true
    config: ".coding-standards.yaml"
    
  spec_compliance:
    enabled: true
    spec_dir: "docs/specs/"

ignore:
  files:
    - "**/test/**"
    - "**/generated/**"
  rules:
    - UC5  # Skip transaction check for specific cases

severity_thresholds:
  error: 0    # Block if any errors
  warning: 5  # Block if > 5 warnings
```
