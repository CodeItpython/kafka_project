<template>
  <div :class="$style.container">
    <div :class="$style.card">
      <h2 :class="$style.cardTitle">{{ isLogin ? 'Login' : 'Sign Up' }}</h2>
      <form @submit.prevent="authenticate">
        <div :class="$style.formControl">
          <label :class="$style.formLabel" for="username">Username:</label>
          <input type="text" id="username" :class="$style.input" v-model="username" required autocomplete="username" />
        </div>
        <div :class="$style.formControl">
          <label :class="$style.formLabel" for="password">Password:</label>
          <input type="password" id="password" :class="$style.input" v-model="password" required autocomplete="current-password" />
        </div>
        <button type="submit" :class="$style.buttonPrimary">{{ isLogin ? 'Login' : 'Sign Up' }}</button>
      </form>
      <button @click="oauth2Login('kakao')" :class="$style.buttonWarning">
        <img src="/kakao_logo.png" alt="Kakao Logo" :class="$style.kakaoLogo" />
        Login with Kakao
      </button>
      <p :class="$style.textCenter">
        <a href="#" @click.prevent="toggleForm" :class="$style.link">
          {{ isLogin ? 'Need an account? Sign Up' : 'Already have an account? Login' }}
        </a>
      </p>
      <div v-if="errorMessage" :class="$style.alertDanger" role="alert">
        {{ errorMessage }}
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import { css } from 'vue-emotion';

export default {
  name: 'Login',
  data() {
    return {
      username: '',
      password: '',
      isLogin: true,
      errorMessage: '',
    };
  },
  methods: {
    async authenticate() {
      this.errorMessage = '';
      try {
        let response;
        if (this.isLogin) {
          response = await axios.post('http://localhost:8080/api/auth/login', {
            username: this.username,
            password: this.password,
          });
          localStorage.setItem('jwtToken', response.data.accessToken); // 백엔드 응답에 accessToken 필드가 있다고 가정
          localStorage.setItem('userId', response.data.userId);
          localStorage.setItem('username', response.data.username);
          this.$router.push('/friends');
        } else {
          response = await axios.post('http://localhost:8080/api/auth/register', {
            username: this.username,
            email: `${this.username}-${Date.now()}@example.com`, // Generate a more unique email
            password: this.password,
          });
          alert(response.data);
          this.isLogin = true; // Switch to login after successful signup
        }
      } catch (error) {
        this.errorMessage = error.response?.data || 'An error occurred';
        console.error('Authentication error:', error);
      }
    },
    oauth2Login(provider) {
      window.location.href = `${process.env.VITE_APP_BACKEND_URL}/oauth2/callback/${provider}`;
    },
    toggleForm() {
      this.isLogin = !this.isLogin;
      this.errorMessage = '';
    },
  },
  computed: {
    $style: () => ({
      container: css`
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
      `,
      card: css`
        padding: 2rem;
        max-width: 400px;
        width: 100%;
        border-width: 1px;
        border-radius: 0.5rem;
        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
      `,
      cardTitle: css`
        font-size: 1.875rem;
        font-weight: bold;
        text-align: center;
        margin-bottom: 1rem;
      `,
      formControl: css`
        margin-bottom: 0.75rem;
      `,
      formLabel: css`
        display: block;
        margin-bottom: 0.25rem;
        font-weight: 500;
      `,
      input: css`
        display: block;
        width: 100%;
        padding: 0.5rem 0.75rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        &:focus {
          outline: none;
          border-color: #3182ce;
          box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.5);
        }
      `,
      buttonPrimary: css`
        display: block;
        width: 100%;
        padding: 0.75rem 1rem;
        background-color: #3182ce;
        color: white;
        border-radius: 0.375rem;
        margin-bottom: 0.75rem;
        cursor: pointer;
        &:hover {
          background-color: #2b6cb0;
        }
      `,
      buttonWarning: css`
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        padding: 0.75rem 1rem;
        background-color: #f6e05e;
        color: #2d3748;
        border-radius: 0.375rem;
        margin-bottom: 0.75rem;
        cursor: pointer;
        &:hover {
          background-color: #ecc94b;
        }
      `,
      kakaoLogo: css`
        height: 20px;
        margin-right: 8px;
      `,
      textCenter: css`
        text-align: center;
        margin-top: 0.75rem;
      `,
      link: css`
        color: #3182ce;
        text-decoration: none;
        &:hover {
          text-decoration: underline;
        }
      `,
      alertDanger: css`
        padding: 1rem;
        background-color: #fed7d7;
        color: #c53030;
        border-radius: 0.375rem;
        margin-top: 0.75rem;
      `,
    }),
  },
};
</script>