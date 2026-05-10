const fs = require("fs");
const path = require("path");
const dir = "d:/MySpace/niro/niro-client/src/views";
const files = [
  "UnboxRecord.vue",
  "OrderRecord.vue",
  "InviteCodeManage.vue",
  "InventoryManagement.vue",
  "C5SnipingTaskV2.vue",
  "C5SnipingAccountConfig.vue",
];

for (const file of files) {
  const filePath = path.join(dir, file);
  let content = fs.readFileSync(filePath, "utf8");

  // Regex to add props to <t-pagination if they are not already there
  content = content.replace(
    /<t-pagination/g,
    `<t-pagination
            :size="isMobile ? 'small' : 'medium'"
            :theme="isMobile ? 'simple' : 'default'"
            :show-page-size="isMobile ? false : undefined"`
  );

  fs.writeFileSync(filePath, content);
  console.log("Updated " + file);
}
