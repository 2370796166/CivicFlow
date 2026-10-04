<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import StatePanel from '@/components/StatePanel.vue'
import type { AdminUser } from '@/types/admin'
import type { Role } from '@/types/api'
import { adminErrorMessage } from '@/utils/adminError'

const rows = ref<AdminUser[]>([]), page = ref(1), total = ref(0)
const keyword = ref(''), status = ref('')
const loading = ref(false), loadError = ref(false), busy = ref(false)
const dialog = ref(false), formError = ref('')
const form = reactive({ username: '', mobile: '', password: '', displayName: '', roles: ['USER'] as Role[] })
const rolesDialog = ref(false), target = ref<AdminUser | null>(null), selectedRoles = ref<Role[]>([])
async function load() {
  loading.value = true; loadError.value = false
  try { const result = await adminApi.users({ page: page.value, size: 20, keyword: keyword.value.trim() || undefined, status: status.value || undefined }); rows.value = result.items; total.value = result.total }
  catch { loadError.value = true }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function create() { Object.assign(form, { username: '', mobile: '', password: '', displayName: '', roles: ['USER'] }); formError.value = ''; dialog.value = true }
function validate(): string {
  if (!/^[A-Za-z][A-Za-z0-9._-]{2,63}$/.test(form.username)) return '用户名需为 3–64 位，首位为字母。'
  if (form.mobile && !/^(?:\+?86)?1[3-9]\d{9}$/.test(form.mobile)) return '手机号格式不正确。'
  if (form.password.length < 8 || form.password.length > 72) return '密码需为 8–72 位。'
  if (!form.displayName.trim() || form.displayName.length > 64) return '显示名称不能为空且最多 64 字。'
  if (!form.roles.length) return '至少选择一个角色。'
  return ''
}
async function save() {
  formError.value = validate()
  if (formError.value || busy.value) return
  busy.value = true
  if (form.roles.some((role) => role === 'STAFF' || role === 'ADMIN')) {
    try { await ElMessageBox.confirm(`将创建拥有 ${form.roles.join('、')} 角色的账号“${form.username}”。确认授权？`, '确认创建高权限账号', { type: 'warning' }) }
    catch { busy.value = false; return }
  }
  try { await adminApi.createUser({ username: form.username, mobile: form.mobile || null, password: form.password, displayName: form.displayName.trim(), roles: form.roles }); form.password = ''; dialog.value = false; await load() }
  catch (error) { formError.value = adminErrorMessage(error); await load() }
  finally { form.password = ''; busy.value = false }
}
async function changeStatus(row: AdminUser) {
  if (busy.value) return
  busy.value = true
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try { await ElMessageBox.confirm(`确认${next === 'DISABLED' ? '禁用' : '启用'}用户“${row.displayName}”？禁用会使其现有会话失效。`, '确认账号状态', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.userStatus(row, next); await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
function editRoles(row: AdminUser) { target.value = row; selectedRoles.value = [...row.roles]; rolesDialog.value = true }
async function saveRoles() {
  if (!target.value || busy.value) return
  if (!selectedRoles.value.length) { ElMessage.error('至少保留一个角色'); return }
  busy.value = true
  try { await ElMessageBox.confirm(`确认将“${target.value.displayName}”的角色替换为 ${selectedRoles.value.join('、')}？现有会话将失效。`, '二次确认角色', { type: 'warning' }) }
  catch { busy.value = false; return }
  try { await adminApi.userRoles(target.value, selectedRoles.value); rolesDialog.value = false; await load() }
  catch (error) { ElMessage.error(adminErrorMessage(error)); await load() }
  finally { busy.value = false }
}
onMounted(() => { void load() })
</script>

<template>
  <div class="page-heading">
    <span class="eyebrow">管理员 / 身份与角色</span><h1>用户与角色管理</h1><p>仅显示脱敏手机号；角色和账号状态由服务端复核。</p>
  </div>
  <div class="content-card admin-card">
    <div class="admin-toolbar">
      <el-input
        v-model="keyword"
        placeholder="用户名、显示名或完整手机号"
        @keyup.enter="search"
      /><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        @change="search"
      >
        <el-option
          label="启用"
          value="ENABLED"
        /><el-option
          label="停用"
          value="DISABLED"
        /><el-option
          label="锁定"
          value="LOCKED"
        />
      </el-select><el-button @click="search">
        查询
      </el-button><el-button
        type="primary"
        @click="create"
      >
        新增用户
      </el-button>
    </div><StatePanel
      v-if="loading"
      state="loading"
    /><StatePanel
      v-else-if="loadError"
      state="error"
      @retry="load"
    /><StatePanel
      v-else-if="!rows.length"
      state="empty"
      title="没有符合条件的用户"
    /><el-table
      v-else
      :data="rows"
      stripe
      class="admin-table"
    >
      <el-table-column
        prop="username"
        label="用户名"
        min-width="155"
      /><el-table-column
        prop="displayName"
        label="显示名"
        min-width="135"
      /><el-table-column
        prop="maskedMobile"
        label="手机号"
        min-width="145"
      /><el-table-column
        label="角色"
        min-width="165"
      >
        <template #default="{ row }">
          {{ row.roles.join('、') }}
        </template>
      </el-table-column><el-table-column
        prop="status"
        label="状态"
        width="95"
      /><el-table-column
        label="操作"
        min-width="160"
      >
        <template #default="{ row }">
          <el-button
            text
            :disabled="busy"
            @click="editRoles(row)"
          >
            角色
          </el-button><el-button
            text
            :disabled="busy"
            @click="changeStatus(row)"
          >
            {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table><el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      background
      layout="prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
  <el-dialog
    v-model="dialog"
    title="新增用户"
    width="min(560px, 94vw)"
    destroy-on-close
  >
    <form
      class="admin-form"
      @submit.prevent="save"
    >
      <label>用户名<input
        v-model="form.username"
        autocomplete="off"
        maxlength="64"
        required
      ></label><label>手机号（可选）<input
        v-model="form.mobile"
        autocomplete="off"
        inputmode="tel"
      ></label><label>显示名称<input
        v-model="form.displayName"
        maxlength="64"
        required
      ></label><label>初始密码<input
        v-model="form.password"
        type="password"
        autocomplete="new-password"
        minlength="8"
        maxlength="72"
        required
      ></label><span>角色</span><el-checkbox-group v-model="form.roles">
        <el-checkbox value="USER">
          普通用户
        </el-checkbox><el-checkbox value="STAFF">
          窗口人员
        </el-checkbox><el-checkbox value="ADMIN">
          管理员
        </el-checkbox>
      </el-checkbox-group><p
        v-if="formError"
        role="alert"
        class="admin-error"
      >
        {{ formError }}
      </p><div class="admin-dialog-actions">
        <el-button @click="dialog = false; form.password = ''">
          取消
        </el-button><el-button
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          创建用户
        </el-button>
      </div>
    </form>
  </el-dialog>
  <el-dialog
    v-model="rolesDialog"
    title="替换用户角色"
    width="min(500px, 94vw)"
    destroy-on-close
  >
    <p>用户：{{ target?.displayName }}</p><el-checkbox-group v-model="selectedRoles">
      <el-checkbox value="USER">
        普通用户
      </el-checkbox><el-checkbox value="STAFF">
        窗口人员
      </el-checkbox><el-checkbox value="ADMIN">
        管理员
      </el-checkbox>
    </el-checkbox-group><div class="admin-dialog-actions">
      <el-button @click="rolesDialog = false">
        取消
      </el-button><el-button
        type="primary"
        :loading="busy"
        @click="saveRoles"
      >
        确认角色
      </el-button>
    </div>
  </el-dialog>
</template>
