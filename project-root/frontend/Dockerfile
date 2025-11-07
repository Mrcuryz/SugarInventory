# 使用官方轻量 nginx 镜像
FROM nginx:alpine

# 清除默认页面
RUN rm -rf /usr/share/nginx/html/*

# 复制前端构建产物到 nginx 的默认目录
COPY dist/ /usr/share/nginx/html/

# 复制我们写好的 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
