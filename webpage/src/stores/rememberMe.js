import {defineStore} from 'pinia'
import {ref} from 'vue'
import {sanitizeRememberedLogin} from '@/utils/rememberedLogin.mjs'

const rememberMeStore = defineStore('userInfo', () => {
    //定义状态相关的内容

    const info = ref({})

    const setInfo = (newInfo) => {
        info.value = sanitizeRememberedLogin(newInfo)
    }


    const removeInfo = () => {
        info.value = {}
    }

    const sanitizeInfo = () => {
        info.value = sanitizeRememberedLogin(info.value)
    }

    return {info, setInfo, removeInfo, sanitizeInfo}

}, {persist: true})
export default rememberMeStore;
