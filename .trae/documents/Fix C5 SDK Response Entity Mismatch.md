**Phase 1: Diagnosis & Verification (诊断与验证)**

1. Create a temporary Python script `niro-spider/tests/test_c5_response.py` to fetch the actual API response from C5.

   * **Endpoint**: `POST https://openapi.c5game.com/merchant/market/v2/products/condition/hash/name`

   * **Payload**: `{ "appId": 730, "marketHashName": "Sealed Genesis Terminal", "pageNum": 1, "pageSize": 10 }`

   * **Auth**: Query parameter `app-key=32a417bee57a445a9a09e58405686927`
2. Run the script to capture the raw JSON response.
3. Analyze the JSON structure, focusing on the `wear` (wear value) field location and naming, and compare it with the current `C5ProductSearchResponse.java` definition.

**Phase 2: Fix Implementation (修复实现)**

1. **Update Response Entity**: Based on the analysis, modify `niro-sdk/.../C5ProductSearchResponse.java` (and potentially `C5AssetInfo` / `C5ItemInfo`) to correctly map the JSON fields.

   * Likely scenario: The `wear` field might be directly under the product item or named differently (e.g., snake\_case vs camelCase issues if Jackson is not configured correctly, though code shows `FAIL_ON_UNKNOWN_PROPERTIES=false`).
2. **Update Business Logic**: Refactor `C5TradeStrategyImpl.java`, specifically the `checkWear` method, to access the wear value from the correct field.

**Phase 3: Validation (验证)**

1. Run `mvn clean compile` in `niro-server` to ensure all changes are syntactically correct and type-safe.
2. Review the code changes to ensure they follow project conventions (Hutool usage, strict dependency injection).

