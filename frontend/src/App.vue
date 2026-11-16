<template>
  <main class="sku-page">
    <header class="page-header">
      <div><h1>SKU 选品</h1><p>筛选商品并批量加入选品池</p></div>
      <el-button @click="refreshList">刷新列表</el-button>
    </header>
    <section class="filters" aria-label="筛选条件">
      <el-form :model="filters" inline label-position="left" @submit.prevent="applyFilters">
        <el-form-item label="商品"><el-input v-model.trim="filters.keyword" placeholder="商品名称或 SKU 编码" clearable style="width: 220px" @keyup.enter="applyFilters" /></el-form-item>
        <el-form-item label="类目"><el-select v-model="filters.category" placeholder="全部类目" clearable style="width: 140px"><el-option v-for="category in categories" :key="category" :label="category" :value="category" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px"><el-option label="可选品" value="available" /><el-option label="库存不足" value="low-stock" /><el-option label="已下架" value="offline" /></el-select></el-form-item>
        <el-form-item label="售价"><div class="price-range"><el-input-number v-model="filters.minPrice" :min="0" :precision="2" :controls="false" placeholder="最低价" aria-label="最低售价" /><span>至</span><el-input-number v-model="filters.maxPrice" :min="0" :precision="2" :controls="false" placeholder="最高价" aria-label="最高售价" /></div></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
    </section>
    <section class="results" aria-label="SKU 列表">
      <div class="results-header"><div class="results-title"><h2>商品列表</h2><span>共 {{ filteredRows.length }} 条</span></div><div class="batch-actions"><span class="selection-count">已选 {{ selectedRows.length }} 项</span><el-button type="primary" :disabled="selectedRows.length === 0" @click="addSelected">批量加入选品池</el-button><el-button :disabled="selectedRows.length === 0" @click="clearSelection">清空选择</el-button></div></div>
      <el-table ref="tableRef" :data="pageRows" row-key="id" empty-text="没有符合条件的商品" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" :reserve-selection="true" :selectable="isSelectable" />
        <el-table-column label="商品信息" min-width="260"><template #default="{ row }"><div class="product-name">{{ row.name }}</div><div class="sku-code">{{ row.code }}</div></template></el-table-column>
        <el-table-column prop="category" label="类目" width="120" />
        <el-table-column label="规格" min-width="150"><template #default="{ row }"><span class="specification">{{ row.specification }}</span></template></el-table-column>
        <el-table-column label="售价" width="120" align="right"><template #default="{ row }">¥{{ row.price.toFixed(2) }}</template></el-table-column>
        <el-table-column label="库存" width="100" align="right"><template #default="{ row }"><span :class="{ 'stock-low': row.stock < 20 }">{{ row.stock }}</span></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><span class="status" :class="`status--${row.status}`">{{ statusLabels[row.status as SkuStatus] }}</span></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="!isSelectable(row)" @click="addOne(row)">加入选品池</el-button></template></el-table-column>
      </el-table>
      <footer class="table-footer"><span>仅可选择状态为“可选品”的商品</span><el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="filteredRows.length" layout="total, sizes, prev, pager, next" @size-change="currentPage = 1" /></footer>
    </section>
  </main>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
type SkuStatus = 'available' | 'low-stock' | 'offline'
interface Sku { id: number; code: string; name: string; category: string; specification: string; price: number; stock: number; status: SkuStatus }
interface Filters { keyword: string; category: string; status: SkuStatus | ''; minPrice: number | undefined; maxPrice: number | undefined }
const statusLabels: Record<SkuStatus, string> = { available: '可选品', 'low-stock': '库存不足', offline: '已下架' }
const initialFilters = (): Filters => ({ keyword: '', category: '', status: '', minPrice: undefined, maxPrice: undefined })
const filters = reactive<Filters>(initialFilters())
const appliedFilters = ref<Filters>(initialFilters())
const tableRef = ref<TableInstance>()
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<Sku[]>([])
// 示例数据；后端提供 SKU 接口后可替换数据源。
const rows = ref<Sku[]>([
  { id: 1, code: 'SKU-BAD-001', name: '碳素羽毛球拍', category: '羽毛球', specification: '4U / 白色', price: 269, stock: 128, status: 'available' },
  { id: 2, code: 'SKU-FIT-002', name: '高密度瑜伽垫', category: '健身训练', specification: '6 mm / 深灰', price: 89.9, stock: 46, status: 'available' },
  { id: 3, code: 'SKU-RUN-003', name: '轻量运动水壶', category: '运动配件', specification: '600 ml / 透明', price: 39, stock: 8, status: 'low-stock' },
  { id: 4, code: 'SKU-FIT-004', name: '训练弹力带套装', category: '健身训练', specification: '三件套', price: 59, stock: 0, status: 'offline' },
  { id: 5, code: 'SKU-BAD-005', name: '耐打羽毛球', category: '羽毛球', specification: '12 只装', price: 78, stock: 63, status: 'available' },
])
const categories = computed(() => [...new Set(rows.value.map((row) => row.category))].sort())
const filteredRows = computed(() => {
  const { keyword, category, status, minPrice, maxPrice } = appliedFilters.value
  const q = keyword.toLowerCase()
  return rows.value.filter((row) => (!q || row.name.toLowerCase().includes(q) || row.code.toLowerCase().includes(q)) && (!category || row.category === category) && (!status || row.status === status) && (minPrice == null || row.price >= minPrice) && (maxPrice == null || row.price <= maxPrice))
})
const pageRows = computed(() => filteredRows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
function isSelectable(row: Sku): boolean { return row.status === 'available' }
function handleSelectionChange(selection: Sku[]) { selectedRows.value = selection }
function clearSelection() { tableRef.value?.clearSelection() }
function applyFilters() {
  if (filters.minPrice != null && filters.maxPrice != null && filters.minPrice > filters.maxPrice) { ElMessage.warning('最低价不能高于最高价'); return }
  appliedFilters.value = { ...filters }; currentPage.value = 1; clearSelection()
}
function resetFilters() { Object.assign(filters, initialFilters()); applyFilters() }
async function addSelected() {
  const items = [...selectedRows.value]
  if (!items.length) return
  try { await ElMessageBox.confirm(`确认将选中的 ${items.length} 个 SKU 加入选品池？`, '批量选品', { confirmButtonText: '确认加入', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  // 后端尚无 SKU 选品接口；此处为页面交互演示。
  ElMessage.info(`演示模式：已选择 ${items.length} 个 SKU，尚未保存`)
  clearSelection()
}
function addOne(row: Sku) { ElMessage.info(`演示模式：已选择 ${row.name}，尚未保存`) }
function refreshList() { clearSelection(); ElMessage.info('演示模式：当前显示示例数据') }
</script>
<style scoped>
.sku-page { max-width: 1500px; margin: 0 auto; padding: 28px 32px 48px; color: #20252b; }
.page-header, .results-header, .results-title, .batch-actions, .table-footer { display: flex; align-items: center; }
.page-header, .results-header, .table-footer { justify-content: space-between; }
.page-header { margin-bottom: 28px; }
h1, h2, p { margin: 0; }
h1 { font-size: 22px; font-weight: 650; line-height: 1.4; }
.page-header p { margin-top: 5px; color: #737b85; font-size: 13px; }
.filters { padding: 18px 20px 2px; background: #f7f8fa; }
.filters :deep(.el-form-item) { margin-right: 20px; margin-bottom: 16px; }
.filters :deep(.el-form-item__label) { color: #535b65; }
.price-range { display: flex; align-items: center; gap: 7px; }
.price-range :deep(.el-input-number) { width: 100px; }
.price-range :deep(.el-input__inner) { text-align: left; }
.price-range span { color: #9299a2; }
.results { margin-top: 30px; }
.results-header { gap: 16px; margin-bottom: 14px; }
.results-title { gap: 10px; white-space: nowrap; }
.results-title h2 { font-size: 16px; font-weight: 600; }
.results-title span, .selection-count, .table-footer { color: #858c95; font-size: 13px; }
.batch-actions { flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.batch-actions :deep(.el-button + .el-button) { margin-left: 0; }
.selection-count { margin-right: 5px; }
.product-name { color: #20252b; font-weight: 500; line-height: 1.5; }
.sku-code { margin-top: 3px; color: #9299a2; font-size: 12px; }
.specification { color: #646c76; }
.stock-low { color: #b56b18; }
.status { display: inline-flex; align-items: center; gap: 6px; color: #646c76; white-space: nowrap; }
.status::before { width: 6px; height: 6px; border-radius: 50%; background: #aeb5bd; content: ''; }
.status--available::before { background: #4b9b6b; }
.status--low-stock::before { background: #d2943f; }
.table-footer { gap: 16px; margin-top: 18px; }
@media (max-width: 760px) {
  .sku-page { padding: 20px 16px 36px; }
  .page-header { margin-bottom: 22px; }
  .results-header, .table-footer { align-items: flex-start; flex-direction: column; }
  .batch-actions { justify-content: flex-start; }
  .table-footer :deep(.el-pagination) { max-width: 100%; flex-wrap: wrap; }
}
</style>
