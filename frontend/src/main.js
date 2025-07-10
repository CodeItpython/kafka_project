window.global = window;

import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import axios from 'axios'; // axios 임포트 추가

// Axios 인터셉터 설정
axios.interceptors.request.use(
  config => {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

const app = createApp(App);
app.use(router);
app.mount('#app');