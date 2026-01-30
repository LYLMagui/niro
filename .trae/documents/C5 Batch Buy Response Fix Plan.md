**Phase 1: Diagnosis & Verification (诊断与验证)**

1. Create a Python script `niro-spider/tests/test_c5_batch_buy_response.py` to simulate a batch buy request to C5 API.

   * **Endpoint**: `POST https://openapi.c5game.com/merchant/trade/v1/batch/buy`

   * **Payload**: Use dummy product IDs or low-value items (if possible) or simply rely on the error response structure if authentication/params are enough to trigger a structured response. *Crucially, we need to see the JSON keys for success/failure lists.*

   * **Auth**: `app-key=32a417bee57a445a9a09e58405686927`
2. Run the script and capture the raw JSON response.
3. Compare the JSON keys (e.g., `success_list` vs `successList`, snake\_case vs camelCase) with `C5BatchBuyResponse.java`.

**Phase 2: Fix Implementation (修复实现)**

1. **Update Response Entity**: Modify `niro-sdk/.../C5BatchBuyResponse.java` to match the actual JSON structure.

   * *Hypothesis*: The field names in `C5BatchBuyResponse` likely use camelCase by default in Jackson, but the API returns snake\_case (e.g., `out_trade_no`, `product_id`). Although `@JsonProperty` is used, nested classes or some specific fields might be missing annotations or have typos.
2. **Review Client Logic**: Ensure `C5TradeClient` uses the correct request/response types.

**Phase 3: Validation (验证)**

1. Run `mvn clean compile` in `niro-server`.
2. Verify that the new structure allows the `C5TradeStrategyImpl` to correctly parse the success/failure lists and update order status.

