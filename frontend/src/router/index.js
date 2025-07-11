import { createRouter, createWebHistory } from 'vue-router';
import Login from '../components/Login.vue';
import FriendList from '../components/FriendList.vue';
import Chat from '../components/Chat.vue';
import OAuth2RedirectHandler from '../components/OAuth2RedirectHandler.vue';
import ProfileEdit from '../components/ProfileEdit.vue'; // Import the new component

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'Login', component: Login },
  { path: '/friends', name: 'FriendList', component: FriendList, meta: { requiresAuth: true } },
  { path: '/chat/:friendId/:friendUsername', name: 'Chat', component: Chat, props: true, meta: { requiresAuth: true } },
  { path: '/oauth2/redirect', name: 'OAuth2Redirect', component: OAuth2RedirectHandler },
  { path: '/profile/edit', name: 'ProfileEdit', component: ProfileEdit, meta: { requiresAuth: true } }, // New route
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const loggedIn = localStorage.getItem('userId');
  // OAuth2 redirect path should not be protected
  if (to.path === '/oauth2/redirect') {
    return next();
  }
  if (to.matched.some(record => record.meta.requiresAuth) && !loggedIn) {
    next('/login');
  } else {
    next();
  }
});

export default router;