---
name: coding-standards
description: Niro 项目专用编码规范，覆盖 Java, Vue3, Python，强制执行 rules 目录下的开发军规。
---

# Niro Coding Standards

## 触发时机
- 编写新代码时 (Feature Implementation)
- 代码重构时 (Refactoring)
- 修复 Bug 时 (Bug Fix)

## 核心原则
1. **结论先行**: 所有的注释、Commit Message、PR 描述必须结论先行。
2. **最小修改**: 只改动必要的部分，严禁为了“看起来顺眼”而改动无关代码。
3. **中文优先**: 思考链、注释、文档全部使用中文。

---

## 1. Java 后端规范 (Spring Boot)

### A. 依赖注入 (Dependency Injection)

**✅ 正确示例 (Constructor Injection)**
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final RedisUtil redisUtil;
    
    // Lombok 自动生成构造函数
}
```

**❌ 错误示例 (Field Injection)**
```java
@Service
public class OrderService {
    @Autowired  // 严禁使用
    private OrderMapper orderMapper;
}
```

### B. 数据库查询 (MyBatis-Plus)

**✅ 正确示例 (Lambda Query)**
```java
public Order getByOrderNo(String orderNo) {
    return this.lambdaQuery()
        .eq(Order::getOrderNo, orderNo)
        .one();
}
```

**❌ 错误示例 (Magic String / QueryWrapper)**
```java
public Order getByOrderNo(String orderNo) {
    QueryWrapper<Order> wrapper = new QueryWrapper<>();
    wrapper.eq("order_no", orderNo); // 严禁魔法值字段名
    return orderMapper.selectOne(wrapper);
}
```

### C. 业务断言 (Assertions)

**✅ 正确示例 (Assert)**
```java
public void shipOrder(String orderId) {
    Assert.notBlank(orderId, "订单号不能为空");
    Order order = getById(orderId);
    Assert.notNull(order, "订单不存在");
}
```

**❌ 错误示例 (If Throw)**
```java
public void shipOrder(String orderId) {
    if (orderId == null || orderId.trim().isEmpty()) {
        throw new RuntimeException("订单号不能为空");
    }
}
```

### D. 工具库 (Hutool)

| 操作 | ✅ 推荐 (Hutool) | ❌ 禁止 (JDK/Guava/Apache) |
|------|------------------|----------------------------|
| 字符串判空 | `StrUtil.isBlank(str)` | `StringUtils.isBlank(str)` |
| 集合判空 | `CollUtil.isEmpty(list)` | `CollectionUtils.isEmpty(list)` |
| 对象拷贝 | `BeanUtil.copyProperties(src, dest)` | `BeanUtils.copyProperties` |
| JSON | Mapper 层处理 | Service 层处理 |

---

## 2. 前端规范 (Vue 3 + TS)

### A. 组件结构

**✅ 正确示例 (Script Setup)**
```vue
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useUserStore } from '@/store/user'; // 必须使用别名 @

const userStore = useUserStore();
const count = ref(0);
</script>
```

### B. API 调用

**✅ 正确示例 (Encapsulated API)**
```typescript
// src/api/order.ts
export function createOrder(data: OrderDTO) {
  return request.post('/orders', data);
}

// 组件中使用
import { createOrder } from '@/api/order';
```

**❌ 错误示例 (Direct Axios)**
```typescript
// 组件中直接调用
axios.post('/api/orders', { ... })
```

---

## 3. Python 爬虫规范

### A. 异常处理

**✅ 正确示例 (Decorator)**
```python
@exception_handler
def fetch_market_data(item_id):
    # 业务逻辑
    pass
```

### B. 日志

**✅ 正确示例 (Logger)**
```python
from utils.logger import logger

logger.info(f"开始抓取商品: {item_id}")
```

**❌ 错误示例 (Print)**
```python
print(f"开始抓取商品: {item_id}")
```

---

## 4. 通用规范

### 注释规范
- **严禁行尾注释**: 注释必须在代码上方。
- **口语化**: 像真人一样交流，不要写教科书式的注释。

```java
// ✅ 正确：这里加锁是为了防止并发扣余额
redisUtil.lock(key);

// ❌ 错误：获取 Redis 分布式锁
redisUtil.lock(key); // 这里加锁
```

### 版本管理
- 只有在“任务完成”时才记录版本。
- 使用 `docs/release.md`。
- 格式：`v0.x.0` (功能), `v0.0.x` (Bug修复)。
