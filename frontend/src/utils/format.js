// 统一时间展示：yyyy-MM-dd HH:mm:ss（24 小时制，精确到秒）
// 后端已统一序列化为该字符串，这里做兜底格式化，保证页面展示一致。
const pad = (n) => String(n).padStart(2, '0')

export function fmtTime(t) {
  if (t === null || t === undefined || t === '') return '-'
  const d = t instanceof Date ? t : new Date(t)
  if (isNaN(d.getTime())) return String(t)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
