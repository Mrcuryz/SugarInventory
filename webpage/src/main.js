import './assets/main.scss'
import {createApp} from 'vue'
import router from '@/router'
import App from './App.vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import locale from 'element-plus/dist/locale/zh-cn.js'
import {createPinia} from 'pinia'
import {createPersistedState} from 'pinia-persistedstate-plugin'
import {createI18n} from 'vue-i18n';
import zhCN from './api/translation/zh-CN';

const i18n = createI18n({
    locale: 'zh-CN',
    messages: {'zh-CN': zhCN}
});
const app = createApp(App);
const pinia = createPinia();
const persist = createPersistedState();
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}
pinia.use(persist)
app.use(i18n);
app.use(pinia)
app.use(router)
app.use(ElementPlus, {locale});
app.mount('#app');

