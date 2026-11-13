import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./pages/Home.vue') },
    { path: '/login', component: () => import('./pages/Access.vue'), props: { mode: 'login' } },
    { path: '/register', component: () => import('./pages/Access.vue'), props: { mode: 'register' } },
    { path: '/invite', component: () => import('./pages/Access.vue'), props: { mode: 'invite' } },
    { path: '/owner', component: () => import('./pages/Workspace.vue'), props: { role: 'OWNER' } },
    { path: '/employee', component: () => import('./pages/Workspace.vue'), props: { role: 'EMPLOYEE' } },
    { path: '/s/:slug', component: () => import('./pages/Reserve.vue'), props: true },
    { path: '/:pathMatch(.*)*', component: () => import('./pages/Missing.vue') },
  ],
})

createApp(App).use(router).mount('#app')
