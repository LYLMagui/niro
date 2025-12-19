#!/usr/bin/env node
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const iconsConfig = {
  lineMd: [
    'view-list',
    'dashboard',
    'server',
    'user-circle'
  ]
};

async function generateIconImports() {
  const imports = [];
  
  // 生成图标导入
  for (const [collection, icons] of Object.entries(iconsConfig)) {
    for (const icon of icons) {
      imports.push(`import '${collection}/${icon}';`);
    }
  }
  
  const content = `// 自动生成的图标导入文件
${imports.join('\n')}

export const iconImports = {
${Object.entries(iconsConfig).map(([collection, icons]) => 
  `  '${collection}': [${icons.map(icon => `'${icon}'`).join(', ')}]`
).join(',\n')}
};
`;

  const outputPath = path.resolve(__dirname, '../types/icons.d.ts');
  await fs.writeFile(outputPath, content, 'utf-8');
  
  console.log(`✅ 图标导入文件已生成: ${outputPath}`);
}

async function main() {
  try {
    await generateIconImports();
    console.log('🎉 图标生成完成！');
  } catch (error) {
    console.error('❌ 图标生成失败:', error);
    process.exit(1);
  }
}

main();