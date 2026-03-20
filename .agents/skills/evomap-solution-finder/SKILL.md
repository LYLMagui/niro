# EvoMap Solution Finder

## Trigger Conditions

When user encounters errors, exceptions, or bugs during development and asks:
- "搜索 EvoMap"
- "查找解决方案"
- "look into EvoMap"
- Or provides error logs/signals that could be searched in EvoMap marketplace

## What This Skill Does

Searches the EvoMap AI agent marketplace for validated solutions using error signals or keywords.

## Usage

### Step 1: Extract Error Signals

From the error message, extract key signals:
- Error type: `TimeoutError`, `NullPointerException`, `ConnectionRefused`, etc.
- Framework/library: `Spring Boot`, `Vue`, `React`, etc.
- Keywords: `retry`, `authentication`, `cors`, etc.

### Step 2: Call EvoMap Proxy API

**推荐：使用 compact 模式**（大幅减少 token 消耗）

```bash
curl -X POST http://8.133.242.250:8000/api/opencode/evomap/solutions/search \
  -H "Content-Type: application/json" \
  -d '{
    "signals": ["TimeoutError", "retry"],
    "keywords": ["exponential backoff"],
    "compact": true,
    "limit": 3
  }'
```

Or use signals only:
```bash
curl -X POST http://8.133.242.250:8000/api/opencode/evomap/solutions/search \
  -H "Content-Type: application/json" \
  -d '{
    "signals": ["NullPointerException", "Java"],
    "compact": true,
    "limit": 3
  }'
```

Or use keywords only:
```bash
curl -X POST http://8.133.242.250:8000/api/opencode/evomap/solutions/search \
  -H "Content-Type: application/json" \
  -d '{
    "keywords": ["JWT authentication best practice"],
    "compact": true,
    "limit": 3
  }'
```

### Step 3: Format Results

Present the found solutions with:
- Summary/title
- Confidence score
- Blast radius (impact scope)
- Trigger signals
- Link to full solution (if available)

## API Reference

**Base URL:** `http://8.133.242.250:8000`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/opencode/evomap/solutions/search` | POST | Search solutions by signals or keywords |
| `/health` | GET | Health check |
| `/a2a/stats` | GET | EvoMap hub statistics |

### Search Request

```json
{
  "signals": ["ErrorType", "framework"],  // optional
  "keywords": ["keyword1", "keyword2"],   // optional
  "limit": 3,                            // optional, default 10
  "compact": true                        // optional, return only key fields to reduce token usage
}
```

At least one of `signals` or `keywords` is required.

**compact 模式返回字段**（大幅减少 token）：
- `asset_id` - 资产 ID
- `summary` - 摘要（截断至 200 字符）
- `confidence` - 置信度
- `gdi_score` - GDI 评分
- `trigger` - 触发信号
- `blast_radius` - 影响范围

## Example

User reports: "Spring Boot timeout error when calling external API"

1. Extract signals: `["TimeoutError", "Spring Boot", "HTTP"]`
2. Call API
3. Present solutions with retry mechanism, connection pooling, etc.
