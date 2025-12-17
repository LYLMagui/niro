import { createApp } from 'vue';
import { createPinia } from 'pinia';
import TDesign from 'tdesign-vue-next';
import App from './App.vue';
import router from './router';

// 引入组件库全局样式资源
import 'tdesign-vue-next/es/style/index.css';
// 引入项目全局样式（包含 Tailwind CSS v4）
import './style.css';

// 创建 Vue 应用实例
const app = createApp(App);

// 注册 Pinia 状态管理
app.use(createPinia());
// 注册 Vue Router 路由
app.use(router);
// 注册 TDesign 组件库
app.use(TDesign);

// 挂载应用到 DOM
app.mount('#app');
