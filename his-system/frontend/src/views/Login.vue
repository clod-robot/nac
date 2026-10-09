<template>
  <div class="login-container">
    <div class="login-box">
      <h2 class="title">HIS 医院信息系统</h2>
      <el-form ref="form" :model="form" :rules="rules" label-width="0" @keyup.enter.native="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="el-icon-lock" show-password />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div style="display:flex; width:100%;">
            <el-input v-model="form.captchaCode" placeholder="验证码" prefix-icon="el-icon-key" style="flex:1;" />
            <img :src="captchaImage" class="captcha-img" @click="refreshCaptcha" title="点击刷新" />
          </div>
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="handleLogin">登 录</el-button>
      </el-form>
      <div class="tips">测试账号：admin / doctor / pharmacist，密码：admin123</div>
    </div>
  </div>
</template>

<script>
import { getCaptcha, login } from '@/api/auth'
import store from '@/store'
import router from '@/router'

export default {
  name: 'Login',
  data() {
    return {
      form: { username: '', password: '', captchaKey: '', captchaCode: '' },
      captchaImage: '',
      loading: false,
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
        captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.refreshCaptcha()
  },
  methods: {
    async refreshCaptcha() {
      const res = await getCaptcha()
      this.form.captchaKey = res.data.captchaKey
      this.captchaImage = res.data.image
    },
    async handleLogin() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          const res = await login(this.form)
          store.commit('SET_TOKEN', res.data.token)
          store.commit('SET_USER', { userId: res.data.userId, username: res.data.username, realName: res.data.realName, role: res.data.role })
          this.$message.success('登录成功，欢迎 ' + res.data.realName)
          router.push('/home')
        } catch (e) {
          this.refreshCaptcha()
          this.form.captchaCode = ''
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style scoped>
.login-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e88e5 0%, #1565c0 100%);
}
.login-box {
  width: 380px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,.15);
}
.title { text-align: center; margin: 0 0 28px; color: #1565c0; }
.captcha-img { height: 36px; margin-left: 8px; cursor: pointer; border: 1px solid #dcdfe6; border-radius: 4px; }
.tips { margin-top: 16px; font-size: 12px; color: #909399; text-align: center; }
</style>
