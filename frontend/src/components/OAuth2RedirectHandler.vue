<template>
  <div>
    <p>Processing OAuth2 login...</p>
  </div>
</template>

<script>
export default {
  name: 'OAuth2RedirectHandler',
  mounted() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const userId = urlParams.get('userId');
    const username = decodeURIComponent(urlParams.get('username'));

    if (token && userId && username) {
      localStorage.setItem('jwtToken', token);
      localStorage.setItem('userId', userId); 
      localStorage.setItem('username', username); // Store username
      this.$router.push('/friends');
    } else {
      // Handle error or redirect to login page
      this.$router.push('/login');
    }
  },
};
</script>
