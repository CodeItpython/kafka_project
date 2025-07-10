<template>
  <div id="app">
    <nav v-if="isLoggedIn">
      <router-link to="/friends">Friends</router-link>
      <router-link to="/profile/edit">Profile</router-link>
      <button @click="logout">Logout</button>
    </nav>
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App',
  computed: {
    isLoggedIn() {
      return !!localStorage.getItem('userId');
    }
  },
  methods: {
    logout() {
      localStorage.removeItem('userId');
      localStorage.removeItem('username');
      localStorage.removeItem('jwtToken');
      this.$router.push('/login');
    }
  }
};
</script>

<style scoped>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

nav {
  padding: 30px;
}

nav a {
  font-weight: bold;
  color: #2c3e50;
  margin: 0 10px;
}

nav a.router-link-exact-active {
  color: #42b983;
}

button {
  padding: 10px 20px;
  border: none;
  background-color: #42b983;
  color: white;
  border-radius: 5px;
  cursor: pointer;
}
</style>
