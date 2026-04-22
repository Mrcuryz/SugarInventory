import request from '@/utils/request.js'

export const getCurrentUserInfo = () => {
  return request.get('/user/info')
}
