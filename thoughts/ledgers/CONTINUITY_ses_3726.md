---
session: ses_3726
updated: 2026-02-24T03:58:12.208Z
---

# Session Summary

## Goal
Integrate EvoMap (AI agent evolution marketplace) into the Niro project to enable fetching verified solutions from other AI agents, using OpenCode as the interface.

## Constraints & Preferences
- Backend: Spring Boot 3.5 + Java 21 with multi-module Maven structure (niro-core, niro-sdk, niro-web)
- Primary use case: **Read-only** (fetch solutions), not publishing
- Architecture preference: niro-server as intermediary (not direct EvoMap calls from OpenCode)
- OpenCode plugins: Support skill-based workflows

## Progress
### Done
- [x] Implemented complete EvoMap GEP-A2A protocol client SDK in `niro-server/niro-sdk/src/main/java/com/niro/sdk/evomap/`
  - Core entities: `Gene.java`, `Capsule.java`, `EvolutionEvent.java`, `A2AEnvelope.java`
  - Protocol client: `EvoMapClient.java` with hello(), fetch(), publish(), heartbeat()
  - Service layer: `EvoMapSolutionService.java` with searchByErrorSignals(), searchByKeyword()
  - Configuration: `EvoMapProperties.java` (pure POJO)
  - Enums: AssetType, Intent, AssetStatus, MessageType, Decision, TaskStatus
  - Exception handling: `EvoMapApiException.java`
- [x] Created OpenCode Skill at `.agents/skills/evomap-solution-finder/SKILL.md`
  - Defines trigger conditions (error logs, exceptions)
  - Documents usage flow: extract signals → call API → format output
  - Includes curl template for `POST /api/opencode/evomap/solutions/search`
- [x] Created REST API endpoint in niro-web
  - Controller: `EvoMapSolutionController.java` at `/api/opencode/evomap/solutions/search`
  - DTOs: `EvoMapSolutionSearchRequest.java`, `EvoMapSolutionSearchResponse.java`, `EvoMapSolutionDTO.java`
  - Supports hybrid search strategy (signals + keywords)
- [x] Installed opencode-supermemory plugin
  - Added to `~/.config/opencode/opencode.json`
  - Disabled oh-my-opencode context recovery hook in `oh-my-opencode.json`
- [x] Installed opencode-skillful plugin
  - Added to `~/.config/opencode/opencode.json`
  - Created config `~/.config/opencode/.opencode-skillful.json`
  - Created example skill `writing-git-commits` with SKILL.md and references/style-guide.md
- [x] Verified compilation success for both niro-sdk and niro-web modules

### In Progress
- [ ] Determining runtime architecture (whether to start niro-server or use alternative)

### Blocked
- (none)

## Key Decisions
- **Server-side SDK approach**: Implemented Java SDK in niro-sdk rather than calling EvoMap directly from OpenCode
  - Rationale: Centralizes protocol complexity (heartbeat, retries, auth), enables caching/observability, aligns with Spring Boot architecture
- **Skill + REST API pattern**: OpenCode Skill calls local REST API rather than direct GEP-A2A protocol
  - Rationale: Oracle recommendation - "thin skill layer calling backend API" is most maintainable
- **Lazy loading skills**: Adopted opencode-skillful for on-demand skill injection
  - Rationale: Reduces token usage when not needed, supports 50+ skills efficiently
- **Supermemory integration**: Installed for persistent memory across sessions
  - Rationale: Complements skillful (supermemory = remember facts, skillful = apply expertise)

## Next Steps
1. **Start niro-server** (`mvn spring-boot:run -pl niro-web`) to enable EvoMap API endpoint
2. **Configure EvoMap** in `application.yml` (hub-url, node-id, auto-register)
3. **Test the flow**: Trigger EvoMap skill with an error message → verify solutions returned
4. **(Alternative)** If not starting backend, refactor Skill to call EvoMap directly via curl (more complex, less maintainable)

## Critical Context
- **EvoMap Hub URL**: https://evomap.ai
- **Local API Endpoint**: `POST http://localhost:8080/api/opencode/evomap/solutions/search`
- **Protocol**: GEP-A2A v1.0.0 (HTTP-based, requires SHA256 content-addressable IDs)
- **Skill location**: `.agents/skills/evomap-solution-finder/SKILL.md`
- **Configuration location**: `~/.config/opencode/opencode.json` (plugins), `~/.config/opencode/.opencode-skillful.json` (skillful config)
- **Compilation verified**: Both modules compile successfully with Maven
- **API Key pending**: Supermemory API key not yet configured (skipped by user)

## File Operations
### Read
- `https://evomap.ai/skill.md` (protocol documentation)
- `https://raw.githubusercontent.com/supermemoryai/opencode-supermemory/main/README.md`
- `https://raw.githubusercontent.com/zenobi-us/opencode-skillful/main/README.md`
- `niro-server/pom.xml` (dependency structure)
- `niro-server/niro-web/pom.xml` (module dependencies)

### Modified
- `~/.config/opencode/opencode.json` - Added opencode-supermemory and opencode-skillful plugins
- `~/.config/opencode/oh-my-opencode.json` - Added `disabled_hooks: ["anthropic-context-window-limit-recovery"]`
- `~/.config/opencode/.opencode-skillful.json` - Created skillful configuration
- `niro-server/niro-sdk/src/main/java/com/niro/sdk/evomap/` - Created entire SDK (22 files)
- `niro-server/niro-web/src/main/java/com/niro/web/controller/EvoMapSolutionController.java` - Created REST API
- `niro-server/niro-web/src/main/java/com/niro/web/dto/` - Created 3 DTO files
- `.agents/skills/evomap-solution-finder/SKILL.md` - Created OpenCode skill
- `~/.config/opencode/skills/experts/writing-git-commits/` - Created example skill for skillful
