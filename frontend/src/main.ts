import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { discardUnrelatedDraft } from './bookingDraft'
import './style.css'

declare module 'vue-router' {
  interface RouteMeta {
    background?: 'home' | 'public' | 'detail' | 'workspace'
  }
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./pages/Home.vue'), meta: { background: 'home' } },
    { path: '/login', component: () => import('./pages/Access.vue'), props: { mode: 'login' } },
    { path: '/register', component: () => import('./pages/Access.vue'), props: { mode: 'register' } },
    { path: '/invite', component: () => import('./pages/Access.vue'), props: { mode: 'invite' } },
    { path: '/owner', component: () => import('./pages/Workspace.vue'), props: { role: 'OWNER' }, meta: { background: 'workspace' } },
    { path: '/employee', component: () => import('./pages/Workspace.vue'), props: { role: 'EMPLOYEE' }, meta: { background: 'workspace' } },
    { path: '/s/:slug', component: () => import('./pages/Reserve.vue'), props: true },
    { path: '/libraries', component: () => import('./pages/Libraries.vue'), meta: { background: 'public' } },
    { path: '/services', component: () => import('./pages/Services.vue'), meta: { background: 'public' } },
    { path: '/admin', component: () => import('./pages/Admin.vue'), meta: { background: 'workspace' } },
    { path: '/admin/login', component: () => import('./pages/AdminLogin.vue') },
    { path: '/library-admin', redirect: '/admin?tab=venues' },
    { path: '/library-admin/login', redirect: '/admin/login' },
    { path: '/library-admin/register', redirect: '/admin/login' },
    { path: '/library/:slug', component: () => import('./pages/LibraryReserve.vue'), props: true },
    { path: '/reader', component: () => import('./pages/Reader.vue'), meta: { background: 'public' } },
    { path: '/reader/messages', component: () => import('./pages/Messages.vue') },
    { path: '/library/:slug/social', component: () => import('./pages/SeatSocial.vue'), props: true },
    { path: '/reader/login', component: () => import('./pages/ReaderAccess.vue'), props: { register: false } },
    { path: '/reader/register', component: () => import('./pages/ReaderAccess.vue'), props: { register: true } },
    { path: '/:pathMatch(.*)*', component: () => import('./pages/Missing.vue') },
  ],
})

router.afterEach(to => discardUnrelatedDraft(to.path, to.query.next))
createApp(App).use(router).mount('#app')
