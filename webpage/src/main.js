import './assets/main.scss'
import { createApp } from 'vue'
import router from '@/router'
import App from './App.vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import locale from 'element-plus/dist/locale/zh-cn.js'
import {createPinia} from 'pinia'
import { createPersistedState } from 'pinia-persistedstate-plugin'


const app = createApp(App);
const pinia = createPinia();
const persist = createPersistedState();
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}
pinia.use(persist)
app.use(pinia)
app.use(router)
app.use(ElementPlus,{locale});
app.mount('#app');

