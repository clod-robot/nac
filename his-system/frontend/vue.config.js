// 前端开发代理：所有 /api 请求转发到网关 8080
module.exports = {
  devServer: {
    port: 8088,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  productionSourceMap: false
};
