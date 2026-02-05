# AGENTS.md - Coding Guidelines for Niro Project

## Project Structure

Multi-service application with three components:
- **niro-client**: Vue 3 + TypeScript + Vite frontend
- **niro-server**: Spring Boot 3 + Java 21 backend
- **niro-spider**: Python 3 async web scraper

## Build Commands

### Frontend (niro-client/)
```bash
cd niro-client
pnpm install        # Install dependencies
pnpm dev            # Start dev server
pnpm build          # Production build
pnpm type-check     # TypeScript check
pnpm lint           # Run all linters
pnpm lint:eslint    # ESLint only
pnpm lint:prettier  # Prettier only
```

### Backend (niro-server/)
```bash
cd niro-server
mvn clean install              # Build all modules
mvn clean install -DskipTests  # Build without tests
mvn test                       # Run all tests
mvn test -Dtest=ClassName      # Run single test class
mvn test -Dtest=ClassName#method  # Run single test method
mvn spring-boot:run -pl niro-web  # Run web module
```

### Spider (niro-spider/)
```bash
cd niro-spider
pip install -r requirements.txt
python main.py                    # Start spider
python -m pytest tests/          # Run all tests
python tests/test_file.py        # Run single test file
python -m pytest tests/test_file.py::test_function -v  # Run single test
```

### Docker
```bash
docker-compose -f docker-compose.test.yml up -d    # Test environment
docker-compose -f docker-compose.prod.yml up -d    # Production environment
```

## Code Style Guidelines

### TypeScript/Vue (niro-client)

**Formatting (Prettier)**:
- 2-space indentation, no tabs
- Double quotes, semicolons required
- Print width: 100 characters
- Arrow functions: always parentheses
- Trailing commas: ES5 compatible

**ESLint Rules**:
- No explicit `any` types allowed
- Unused vars must start with `_`
- Vue multi-word component names not enforced
- Auto-imports configured for Vue, VueUse, Vue Router, Pinia

**Vue Conventions**:
- Use `<script setup lang="ts">` syntax
- Use Composition API
- Auto-imported components from TDesign
- Path alias: `@/` maps to `src/`

**Imports**:
```typescript
// External libraries first
import { ref, computed } from "vue";
import { useRoute } from "vue-router";

// Internal imports
import { useUserStore } from "@/stores/user";
import type { UserInfo } from "@/types/user";
```

### Java (niro-server)

**Naming**:
- Packages: `com.niro.module`
- Classes: PascalCase (e.g., `UserService`)
- Methods/Variables: camelCase (e.g., `getUserById`)
- Constants: UPPER_SNAKE_CASE

**Code Style**:
- Java 21 features allowed
- Use Lombok (`@RequiredArgsConstructor`, `@Data`, etc.)
- Constructor injection for dependencies
- Return type: `Result<T>` for all API responses
- Use `Optional` for nullable returns

**Example**:
```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    
    public Result<User> getUser(Long id) {
        User user = userMapper.selectById(id);
        return Result.success(user);
    }
}
```

### Python (niro-spider)

**Style**:
- Use `loguru` for logging (not standard logging)
- Use type hints where possible
- Async/await patterns for I/O operations
- Constants in UPPER_CASE

**Imports**:
```python
# Standard library
import asyncio
import json
from typing import Dict, Any

# Third-party
from loguru import logger
from redis.asyncio import Redis

# Local modules
from config import settings
from utils.logger import setup_logging
```

**Logging**:
- Use emoji indicators for log levels (e.g., `🚀`, `❌`, `⚠️`)
- Include context in messages (taskId, traceId)

## Testing

### Running Single Tests

**Java**:
```bash
mvn test -Dtest=ResponseAdviceTest
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse
```

**Python**:
```bash
python -m pytest tests/test_file.py -v
python -m pytest tests/test_file.py::test_function -v
```

**TypeScript/Vue**: No test framework currently configured

## Git Conventions

**Commit Messages** (Conventional Commits):
- Format: `type(scope): subject`
- Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- Use `pnpm commit` or `git cz` for interactive commit

**Examples**:
```
feat(user): add user authentication
fix(api): resolve null pointer exception
docs(readme): update installation guide
```

**Pre-commit Hooks**:
- ESLint, Prettier, Stylelint run automatically
- Commit message validated against conventional format

## Environment Configuration

- Client: `.env`, `.env.development`, `.env.production`
- Server: `.env` file with Spring profiles (dev/test/prod)
- Spider: Environment variables or `.env` file

## API Documentation

- Server uses Knife4j (enhanced Swagger) at `/doc.html`
- All controllers should use `@Tag` and `@Operation` annotations
- DTOs should use `@Schema` for field documentation

## Error Handling

**Java**:
- Use `@RestControllerAdvice` for global exception handling
- Business exceptions extend `RuntimeException`
- Always return `Result<T>` with appropriate error codes

**Python**:
- Use try/except with specific exceptions
- Log exceptions with `logger.exception()` for stack traces
- Graceful shutdown on SIGINT/SIGTERM

**TypeScript**:
- Use `try/catch` for async operations
- Handle API errors with interceptors
- Display user-friendly error messages via TDesign components
