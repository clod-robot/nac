<template>
  <div class="term-page">
    <div class="bar">
      <el-input v-model="conn.host" size="small" placeholder="主机" style="width:150px" />
      <el-input-number v-model="conn.port" :min="1" :max="65535" size="small" controls-position="right" style="width:110px" />
      <el-input v-model="conn.username" size="small" placeholder="用户名" style="width:120px" />
      <el-input v-model="conn.password" type="password" size="small" placeholder="密码" show-password style="width:140px" @keyup.enter="connect" />
      <el-button type="primary" size="small" @click="connect" :loading="connecting">SSH 连接</el-button>
      <el-button size="small" @click="disconnect">断开</el-button>
      <span class="status" :class="{ on: connected }">{{ connected ? '● 已连接' : '○ 未连接' }}</span>
      <span class="tip">仅管理员可用 · 强制 SSH 登录服务器</span>
    </div>
    <div ref="termEl" class="xterm-box"></div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { useUserStore } from '../store/user'

const store = useUserStore()
const termEl = ref(null)
const connecting = ref(false)
const connected = ref(false)
const conn = reactive({ host: '192.168.10.88', port: 22, username: 'mao', password: '' })

let term = null, fit = null, ws = null, ro = null

onMounted(() => {
  term = new Terminal({
    theme: { background: '#000000', foreground: '#e6e6e6', cursor: '#ffffff', selectionBackground: '#264f78' },
    fontFamily: 'Consolas, "Courier New", monospace',
    fontSize: 14, cursorBlink: true, scrollback: 5000, convertEol: true
  })
  fit = new FitAddon()
  term.loadAddon(fit)
  term.open(termEl.value)
  fit.fit()
  term.writeln('\x1b[36m[NAC 远程终端]\x1b[0m 黑色命令行，请填写连接信息后点「SSH 连接」。')
  term.onData(d => { if (ws && ws.readyState === 1) ws.send(d) })
  ro = new ResizeObserver(() => { try { fit && fit.fit() } catch (e) {} })
  ro.observe(termEl.value)
})

function connect() {
  if (!conn.password) { ElMessage.warning('请输入密码'); return }
  disconnect(false)
  connecting.value = true
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/ssh?token=${encodeURIComponent(store.token)}`)
  ws.onopen = () => {
    connecting.value = false
    ws.send(JSON.stringify({
      host: conn.host, port: conn.port, username: conn.username, password: conn.password,
      cols: term.cols, rows: term.rows
    }))
  }
  ws.onmessage = (e) => { term.write(e.data); connected.value = true }
  ws.onerror = () => { ElMessage.error('连接失败'); connecting.value = false }
  ws.onclose = () => { connected.value = false; connecting.value = false; term.writeln('\r\n\x1b[33m[连接已关闭]\x1b[0m') }
}

function disconnect(write = true) {
  if (ws) { try { ws.close() } catch (e) {} ws = null }
  connected.value = false
  if (write && term) term.writeln('\r\n\x1b[33m[已断开]\x1b[0m')
}

onBeforeUnmount(() => { disconnect(false); if (ro) ro.disconnect(); if (term) term.dispose() })
</script>

<style scoped>
.term-page { display: flex; flex-direction: column; height: 100%; }
.bar { display: flex; align-items: center; gap: 8px; background: #fff; padding: 10px 12px; border-radius: 8px 8px 0 0; flex-wrap: wrap; }
.status { font-size: 12px; color: #909399; }
.status.on { color: #67c23a; }
.tip { color: #c0c4cc; font-size: 12px; margin-left: auto; }
.xterm-box { flex: 1; background: #000; padding: 8px; border-radius: 0 0 8px 8px; overflow: hidden; min-height: 420px; }
</style>
