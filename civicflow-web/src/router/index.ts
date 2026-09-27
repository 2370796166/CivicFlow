import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores/pinia'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types/api'
import AppLayout from '@/components/AppLayout.vue'
import LoginView from '@/views/LoginView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import ExploreView from '@/views/user/ExploreView.vue'
import AppointmentsView from '@/views/user/AppointmentsView.vue'
import AppointmentDetailView from '@/views/user/AppointmentDetailView.vue'
import ReservationView from '@/views/user/ReservationView.vue'
import WorkbenchView from '@/views/staff/WorkbenchView.vue'
import ResourcesView from '@/views/admin/ResourcesView.vue'
import SlotsView from '@/views/admin/SlotsView.vue'
import UsersView from '@/views/admin/UsersView.vue'
import ReconciliationView from '@/views/admin/ReconciliationView.vue'
import UnavailableAdminView from '@/views/admin/UnavailableAdminView.vue'

export const portals: { role: Role; path: string; title: string; subtitle: string }[] = [
  { role: 'USER', path: '/user', title: '我的服务', subtitle: '查找网点、查看预约与排队进度' },
  { role: 'STAFF', path: '/staff', title: '窗口工作台', subtitle: '在授权窗口处理现场业务' },
  { role: 'ADMIN', path: '/admin', title: '管理中心', subtitle: '维护服务配置并查看运行情况' },
]
export function firstPortal(roles: Role[]): string { return portals.find((portal) => roles.includes(portal.role))?.path ?? '/403' }
export function allowedPortals(roles: Role[]) { return portals.filter((portal) => roles.includes(portal.role)) }

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/', redirect: () => firstPortal(useAuthStore(pinia).roles) },
  { path: '/user', component: AppLayout, meta: { role: 'USER' }, children: [
    { path: '', name: 'user', component: ExploreView },
    { path: 'outlets/:outletId', name: 'user-outlet', component: ExploreView },
    { path: 'appointments', name: 'user-appointments', component: AppointmentsView },
    { path: 'appointments/:appointmentId', name: 'user-appointment', component: AppointmentDetailView },
    { path: 'reservations/:reservationId', name: 'user-reservation', component: ReservationView },
  ] },
  { path: '/staff', component: AppLayout, meta: { role: 'STAFF' }, children: [{ path: '', name: 'staff', component: WorkbenchView }] },
  { path: '/admin', component: AppLayout, meta: { role: 'ADMIN' }, children: [
    { path: '', redirect: '/admin/outlets' },
    { path: 'outlets', name: 'admin-outlets', component: ResourcesView, props: { kind: 'outlets' } },
    { path: 'items', name: 'admin-items', component: ResourcesView, props: { kind: 'items' } },
    { path: 'windows', name: 'admin-windows', component: ResourcesView, props: { kind: 'windows' } },
    { path: 'slots', name: 'admin-slots', component: SlotsView },
    { path: 'users', name: 'admin-users', component: UsersView },
    { path: 'reconciliations', name: 'admin-reconciliations', component: ReconciliationView },
    { path: 'appointments', name: 'admin-appointments', component: UnavailableAdminView, props: { title: '预约查询', description: 'API.md 声明了管理员预约查询，但当前 appointment 服务没有对应 Controller；不能展示不完整或越权数据。' } },
    { path: 'operation-logs', name: 'admin-operation-logs', component: UnavailableAdminView, props: { title: '操作日志', description: '当前各服务没有对外分页操作日志查询接口；日志只能在受控后端存储中审计。' } },
  ] },
  { path: '/403', name: 'forbidden', component: ForbiddenView },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { public: true } },
]
export const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.public) {
    if (to.name === 'login' && auth.authenticated) return firstPortal(auth.roles)
    return true
  }
  if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  const required = to.meta.role as Role | undefined
  if (required && !auth.roles.includes(required)) return { name: 'forbidden' }
  return true
})
