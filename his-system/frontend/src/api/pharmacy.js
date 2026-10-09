import request from '@/utils/request'

// 待发药处方列表
export function listPrescriptions(params) {
  return request.get('/api/pharmacy/prescriptions', { params })
}

// 处方详情 + 库存预检查
export function getPrescriptionDetail(id) {
  return request.get(`/api/pharmacy/prescription/${id}`)
}

// 处方审核
export function reviewPrescription(id, approved, opinion) {
  return request.post(`/api/pharmacy/prescription/${id}/review`, null, { params: { approved, opinion } })
}

// 发药（幂等）
export function dispense(prescriptionId, windowNo) {
  return request.post(`/api/pharmacy/dispense/${prescriptionId}`, null, { params: { windowNo } })
}

// 发药统计（ECharts）
export function getStats(days) {
  return request.get('/api/pharmacy/stats', { params: { days } })
}

// 药品目录
export function listDrugs(params) {
  return request.get('/api/pharmacy/drugs', { params })
}

// 处方缴费（发药闭环测试用）
export function payPrescription(id) {
  return request.post(`/api/billing/pay/${id}`)
}
