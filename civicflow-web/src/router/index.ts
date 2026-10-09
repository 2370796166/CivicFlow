import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores/pinia'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types/api'
import AppLayout from '@/components/AppLayout.vue'
const LoginView = () => import('@/views/LoginView.vue')
const ForbiddenView = () => import('@/views/ForbiddenView.vue')
const NotFoundView = () => import('@/views/NotFoundView.vue')
const ExploreView = () => import('@/views/user/ExploreView.vue')
const AppointmentsView = () => import('@/views/user/AppointmentsView.vue')
const AppointmentDetailView = () => import('@/views/user/AppointmentDetailView.vue')
const ReservationView = () => import('@/views/user/ReservationView.vue')
const WorkbenchView = () => import('@/views/staff/WorkbenchView.vue')
const ResourcesView = () => import('@/views/admin/ResourcesView.vue')
const SlotsView = () => import('@/views/admin/SlotsView.vue')
const UsersView = () => import('@/views/admin/UsersView.vue')
const ReconciliationView = () => import('@/views/admin/ReconciliationView.vue')
const AppointmentQueryView = () => import('@/views/admin/AppointmentQueryView.vue')
const OperationLogsView = () => import('@/views/admin/OperationLogsView.vue')

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
    { path: 'appointments', name: 'admin-appointments', component: AppointmentQueryView },
    { path: 'operation-logs', name: 'admin-operation-logs', component: OperationLogsView },
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
