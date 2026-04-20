# Claude Code 配置解析与注释版

> 说明：这是**解读版配置**，方便阅读和维护，不建议直接复制覆盖到实际 `settings.json/settings.local.json`。
> 原因很简单：这里加入了大量注释，而且你贴出来的配置里还混入了终端 ANSI 残留（如 `[1m]`），需要清洗后再用于真实配置。

## 一、注释版配置（JSONC）

```jsonc
{
  // 本地历史/缓存清理周期，单位天。
  // 720 = 保留约 2 年，适合长期使用同一环境的场景。
  "cleanupPeriodDays": 720,

  "env": {
    // Claude API 鉴权 Token。
    // 这是敏感信息，只能放本地，不要提交到仓库。
    "ANTHROPIC_AUTH_TOKEN": "sk-",

    // 自定义 Claude API 网关/转发地址。
    // 说明你不是直连官方默认端点，而是走了一个代理或中转服务。
    "ANTHROPIC_BASE_URL": "https://a-ocnfniawgw.cn-shanghai.fcapp.run",

    // 为所有 HTTP/HTTPS 请求设置本地代理。
    // 常见于科学上网、内网转发、抓包调试场景。
    "HTTPS_PROXY": "http://127.0.0.1:10808",
    "HTTP_PROXY": "http://127.0.0.1:10808",

    // 让 Bash 工具尽量维持在当前项目工作目录内。
    // 对多仓库切换时更稳，能减少路径跑偏问题。
    "CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR": "true",

    // 默认 Haiku / Opus / Sonnet 模型都被你强行指到同一个模型。
    // 这里的值带了 [1m]，看起来像从终端输出里直接拷出来的 ANSI 残留，
    // 如果真实配置文件里也这样写，这些 model id 很可能是无效的。
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": "claude-opus-4-7[1m]",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "claude-opus-4-7[1m]",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "claude-opus-4-7[1m]",

    // 全局默认模型。
    // 同样存在 [1m] 残留问题，真实落盘时应清洗成纯模型 ID。
    "ANTHROPIC_MODEL": "claude-opus-4-7[1m]",

    // 启用实验性 Agent Teams 能力。
    // 一般用于多 agent 协作、并行子任务等高级能力。
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1",

    // 关闭 attribution header。
    // 通常用于减少额外请求头或避免对接系统看到署名头。
    "CLAUDE_CODE_ATTRIBUTION_HEADER": "0",

    // 跳过安装检查。
    // 适合已经确认环境没问题、又不想每次被安装校验打断的场景。
    "DISABLE_INSTALLATION_CHECKS": "1",

    // 禁用非必要网络流量。
    // 有助于降低噪音、减少遥测或额外联网行为。
    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "true",

    // 启用 ToolSearch，允许按需加载 deferred tools。
    // 你这次会话里出现的大量“先 ToolSearch 再调用工具”就是这个机制。
    "ENABLE_TOOL_SEARCH": "1"
  },

  "permissions": {
    "allow": [
      // 允许一组常用读写/检索/任务管理工具直接运行，减少权限确认弹窗。
      // 这条写法是展示态还是原始配置，要留意；
      // 如果真实配置不支持逗号拼接，应该拆成多个独立条目。
      "Read,Edit,Bash,WebFetch,Glob,Grep,WebSearch,TaskList,TaskCreate,TaskGet,TaskUpdate"
    ],
    "deny": [
      // 显式禁止 rm * 这种高破坏命令。
      // 这是一个很重要的保险丝。
      "Bash(rm *)"
    ]
  },

  // CLI 当前主模型选择。
  // 这里同样有 [1m] 残留风险，真实值应是纯 'opus' 或标准模型 ID。
  "model": "opus[1m]",

  "hooks": {
    "Stop": [
      {
        // matcher 为空，表示所有停止事件都触发。
        "matcher": "",
        "hooks": [
          {
            "type": "command",

            // 每次会话停止时异步执行一个通知脚本。
            // 常见用途：桌面提醒、声音提醒、日志记录。
            "command": "bash /home/jing/.claude/hooks/stop-notify.sh",

            // 最长等待 10 秒。
            "timeout": 10,

            // 异步执行，不阻塞主流程退出。
            "async": true
          }
        ]
      }
    ]
  },

  "statusLine": {
    "type": "command",

    // 自定义底部状态栏脚本。
    // 一般用来展示模型、token、分支、耗时、权限状态等。
    "command": "node $HOME/.claude/hud/statusline.mjs"
  },

  "enabledPlugins": {
    // oh-my-claudecode：增强型命令/体验插件。
    "oh-my-claudecode@omc": true,

    // typescript-lsp：TypeScript 语言服务支持。
    "typescript-lsp@claude-plugins-official": true,

    // everything-claude-code：扩展技能/集成集合。
    "everything-claude-code@everything-claude-code": true,

    // superpowers：常见增强能力合集。
    "superpowers@claude-plugins-official": true,

    // planning-with-files：文件式计划管理。
    "planning-with-files@planning-with-files": true,

    // ui-ux-pro-max：UI/UX 设计增强技能。
    "ui-ux-pro-max@ui-ux-pro-max-skill": true,

    // feature-dev：功能开发相关插件。
    "feature-dev@claude-plugins-official": true,

    // frontend-design：前端设计类插件。
    "frontend-design@claude-plugins-official": true
  },

  "extraKnownMarketplaces": {
    // 自定义技能市场源，允许从指定 GitHub 仓库发现/安装插件。
    "superpowers-marketplace": {
      "source": {
        "source": "github",
        "repo": "obra/superpowers-marketplace"
      }
    },
    "planning-with-files": {
      "source": {
        "source": "github",
        "repo": "OthmanAdi/planning-with-files"
      }
    },
    "ui-ux-pro-max-skill": {
      "source": {
        "source": "github",
        "repo": "nextlevelbuilder/ui-ux-pro-max-skill"
      }
    },
    "omc": {
      "source": {
        "source": "github",
        "repo": "yeachan-heo/oh-my-claudecode"
      }
    },
    "everything-claude-code": {
      "source": {
        "source": "github",
        "repo": "affaan-m/everything-claude-code"
      }
    }
  },

  // 默认语言：中文。
  "language": "中文",

  // 跳过 WebFetch 的预检流程，通常能更快，但少一层安全/有效性确认。
  "skipWebFetchPreflight": true,

  // 默认总是启用更深度思考。
  // 好处是回答更稳，代价是更慢、更贵。
  "alwaysThinkingEnabled": true,

  // 推理努力等级：high。
  // 会影响响应质量、耗时和资源消耗。
  "effortLevel": "high",

  // 自动更新通道：latest。
  // 意味着你会更快拿到新特性，但也更容易先吃到变更。
  "autoUpdatesChannel": "latest",

  // 跳过危险模式权限提示。
  // 这个配置很激进，适合你明确知道自己在做什么的环境，
  // 但同时也提高了误操作风险。
  "skipDangerousModePermissionPrompt": true,

  // 这里看起来像自定义环境变量或重复字段。
  // 如果 Claude Code 不识别它，那它只是“摆设”，不会生效。
  "EFFORTLEVEL": "MAX",

  "feedbackSurveyState": {
    // 内部状态字段，记录上次问卷显示时间。
    // 一般不用手改。
    "lastShownTime": 1754010559411
  }
}
```

不带注释原版
```json
`{
  "cleanupPeriodDays": 720,
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "sk-",
    "ANTHROPIC_BASE_URL": "https://a-ocnfniawgw.cn-shanghai.fcapp.run",
    "HTTPS_PROXY": "http://127.0.0.1:10808",
    "HTTP_PROXY": "http://127.0.0.1:10808",
    "CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR": "true",
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": "claude-opus-4-7[1m]",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "claude-opus-4-7[1m]",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "claude-opus-4-7[1m]",
    "ANTHROPIC_MODEL": "claude-opus-4-7[1m]",
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1",
    "CLAUDE_CODE_ATTRIBUTION_HEADER": "0",
    "DISABLE_INSTALLATION_CHECKS": "1",
    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "true",
    "ENABLE_TOOL_SEARCH": "1"
  },
  "permissions": {
    "allow": [
      "Read,Edit,Bash,WebFetch,Glob,Grep,WebSearch,TaskList,TaskCreate,TaskGet,TaskUpdate"
    ],
    "deny": [
      "Bash(rm *)"
    ]
  },
  "model": "opus[1m]",
  "hooks": {
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "bash /home/jing/.claude/hooks/stop-notify.sh",
            "timeout": 10,
            "async": true
          }
        ]
      }
    ]
  },
  "statusLine": {
    "type": "command",
    "command": "node $HOME/.claude/hud/statusline.mjs"
  },
  "enabledPlugins": {
    "oh-my-claudecode@omc": true,
    "typescript-lsp@claude-plugins-official": true,
    "everything-claude-code@everything-claude-code": true,
    "superpowers@claude-plugins-official": true,
    "planning-with-files@planning-with-files": true,
    "ui-ux-pro-max@ui-ux-pro-max-skill": true,
    "feature-dev@claude-plugins-official": true,
    "frontend-design@claude-plugins-official": true
  },
  "extraKnownMarketplaces": {
    "superpowers-marketplace": {
      "source": {
        "source": "github",
        "repo": "obra/superpowers-marketplace"
      }
    },
    "planning-with-files": {
      "source": {
        "source": "github",
        "repo": "OthmanAdi/planning-with-files"
      }
    },
    "ui-ux-pro-max-skill": {
      "source": {
        "source": "github",
        "repo": "nextlevelbuilder/ui-ux-pro-max-skill"
      }
    },
    "omc": {
      "source": {
        "source": "github",
        "repo": "yeachan-heo/oh-my-claudecode"
      }
    },
    "everything-claude-code": {
      "source": {
        "source": "github",
        "repo": "affaan-m/everything-claude-code"
      }
    }
  },
  "language": "中文",
  "skipWebFetchPreflight": true,
  "alwaysThinkingEnabled": true,
  "effortLevel": "high",
  "autoUpdatesChannel": "latest",
  "skipDangerousModePermissionPrompt": true,
  "EFFORTLEVEL": "MAX",
  "feedbackSurveyState": {
    "lastShownTime": 1754010559411
  }
}`
```

## 二、这份配置的整体风格

这套配置的核心倾向很明确：

1. **高自治**：开了大量允许项、关闭了部分检查、跳过危险模式提示。
2. **高性能优先转高能力优先**：默认模型几乎全指向 Opus，且开启 `alwaysThinkingEnabled`。
3. **本地工作流很重**：代理、hooks、状态栏、插件市场、技能插件都配齐了。
4. **偏高级玩家配置**：更像长期重度使用者，而不是默认新手配置。

## 三、逐项作用分析

### 1. cleanupPeriodDays
- 作用：控制本地清理周期。
- 当前效果：两年才清一次，本地缓存/记录保留时间较长。
- 适合：长期连续使用、希望保留上下文痕迹的人。

### 2. env
- 作用：给 Claude Code 运行时注入环境变量。
- 当前效果：
  - 走自定义 API 网关。
  - 所有网络走本地代理 `127.0.0.1:10808`。
  - 默认模型被统一强制到 Opus 4.7。
  - 启用了 ToolSearch、实验性 agent teams。
  - 关闭 attribution header，关闭安装检查，减少非必要流量。
- 风险：
  - `ANTHROPIC_AUTH_TOKEN` 是敏感信息，绝不能入库。
  - 模型字符串里的 `[1m]` 很像脏数据，建议清理。

### 3. permissions
- 作用：控制哪些工具免确认、哪些命令显式拒绝。
- 当前效果：
  - 常见读写、检索、Bash、任务工具基本都免确认。
  - `rm *` 被显式禁止。
- 优点：减少大量权限弹窗，工作流更顺。
- 风险：因为 `Bash` 也在 allow 里，如果再叠加危险模式免提示，整体自主权限偏大。

### 4. model
- 作用：指定当前 CLI 主模型。
- 当前效果：倾向使用 Opus。
- 问题：`opus[1m]` 不是干净值，像终端格式残留。

### 5. hooks.Stop
- 作用：会话停止时自动执行脚本。
- 当前效果：大概率是桌面提醒或声音提醒。
- 价值：适合长任务完成后提醒你回来查看结果。

### 6. statusLine
- 作用：自定义 Claude Code 状态栏。
- 当前效果：底部会显示你自己的 HUD 信息。
- 价值：适合显示 token、模型、分支、权限状态、耗时等运行态数据。

### 7. enabledPlugins
- 作用：启用扩展插件和技能。
- 当前效果：你已经把计划管理、前端设计、UI/UX、TS LSP、增强工具都打开了。
- 价值：说明你不是只把 Claude 当聊天工具，而是在当工程工作台使用。

### 8. extraKnownMarketplaces
- 作用：告诉 Claude 去哪些 GitHub 仓库发现插件/技能市场。
- 当前效果：你的插件来源不只官方，还包括多个社区仓库。
- 风险：社区源更灵活，但要关注版本质量和兼容性。

### 9. language
- 作用：默认输出语言。
- 当前效果：输出中文。

### 10. skipWebFetchPreflight
- 作用：跳过 WebFetch 的预检查。
- 当前效果：访问更快。
- 代价：少一层预校验，遇到异常 URL 时容错会更弱。

### 11. alwaysThinkingEnabled + effortLevel
- 作用：默认深度思考，并把推理强度拉高。
- 当前效果：
  - 回答质量更稳定。
  - 响应时间更长。
  - token / 成本更高。
- 判断：适合复杂开发任务，不适合追求极致低延迟的轻问答。

### 12. autoUpdatesChannel
- 作用：自动更新通道。
- 当前效果：跟 `latest`，会优先拿新版本。
- 风险：功能来得快，潜在行为变化也会更早碰到。

### 13. skipDangerousModePermissionPrompt
- 作用：跳过危险模式提示。
- 当前效果：减少确认步骤。
- 风险：这是整份配置里最激进的项之一。
- 建议：如果你经常做高风险本地操作，最好谨慎保留。

### 14. EFFORTLEVEL
- 作用：看起来想表达“最大努力级别”。
- 当前效果：未必生效。
- 判断：除非 Claude Code 明确认这个字段，否则它更像冗余项。

### 15. feedbackSurveyState
- 作用：内部元数据，记录问卷显示状态。
- 当前效果：基本不影响使用。
- 建议：不用管。

## 四、我认为这份配置里最值得注意的点

### 1. 模型字段疑似被 ANSI 污染
你贴出来的这些值：
- `claude-opus-4-7[1m]`
- `opus[1m]`

很像从带颜色的终端输出里直接复制出来的结果，`[1m]` 是粗体控制码残留。如果这不是“展示文本”，而是真的被写进配置文件，那模型设置大概率有问题。

**建议清洗成类似这样：**
- `claude-opus-4-7`
- `opus`

### 2. 你把所有默认模型都指向了 Opus
这意味着：
- Haiku 不再轻量
- Sonnet 不再平衡
- Opus 成了统一默认

这会让体验更稳定，但也会：
- 更慢
- 更贵
- 失去“按场景切轻模型”的意义

### 3. 权限配置很大胆
你当前是：
- 大量常用工具免确认
- 危险模式提示也跳过
- 只针对 `rm *` 做了明确拦截

这很适合熟练用户，但如果哪天 agent 指令范围理解错了，保护层会偏薄。

### 4. 这是“重度工程化”配置，不是默认办公配置
你的配置里有：
- hook
- status line
- plugin marketplace
- 多技能插件
- 网络代理
- 自定义网关

这说明它的目标不是“偶尔用一下 Claude”，而是把 Claude Code 作为常驻开发环境的一部分。

## 五、建议优化版（不改你的使用习惯，只修明显问题）

如果你想保留现在的整体风格，我建议优先做这几件事：

1. **先清理所有模型值里的 `[1m]` 残留。**
2. **确认 `permissions.allow` 这一条是不是原始文件格式。** 如果不是，拆成多个独立条目更稳。
3. **确认 `EFFORTLEVEL` 是否真被识别。** 如果不生效就删掉，避免误导。
4. **确认 `skipDangerousModePermissionPrompt=true` 是否真符合你的风险承受范围。**
5. **不要把 token 或 base URL 配置提交到仓库。**

## 六、结论

这份配置整体上没有“方向性错误”，反而很像一个熟练用户的高自治开发环境。

真正的问题主要有两个：
- **一是模型字段可能有脏数据**；
- **二是权限和危险模式配置比较激进，需要你明确接受这个风险。**

如果你愿意，我下一步可以直接再给你一份：

1. **可直接落地的精简安全版 `settings.local.json`**
2. **保留高效率但修掉脏数据的优化版 `settings.local.json`**
