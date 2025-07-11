<template>
  <div class="profile-edit">
    <h2>Edit Profile</h2>
    <form @submit.prevent="updateProfile">
      <div class="form-group">
        <label for="username">Username:</label>
        <input type="text" id="username" v-model="username" />
      </div>
      <div class="form-group">
        <label for="profilePicture">Profile Picture:</label>
        <input type="file" id="profilePicture" @change="onFileChange" accept="image/*" />
      </div>
      <button type="submit">Update Profile</button>
    </form>
    <p v-if="updateMessage">{{ updateMessage }}</p>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'ProfileEdit',
  data() {
    return {
      username: '',
      selectedFile: null,
      updateMessage: '',
      userId: null
    };
  },
  methods: {
    onFileChange(event) {
      this.selectedFile = event.target.files[0];
    },
    async getCurrentUser() {
      try {
        const response = await axios.get('https://kafka-project-1x9o.onrender.com/api/users/me', {
          headers: {
            Authorization: `Bearer ${localStorage.getItem('token')}`
          }
        });
        this.userId = response.data.id;
        this.username = response.data.username;
      } catch (error) {
        console.error('Error fetching user data:', error);
      }
    },
    async updateProfile() {
      const formData = new FormData();
      formData.append('username', this.username);
      if (this.selectedFile) {
        formData.append('file', this.selectedFile);
      }

      try {
        const response = await axios.put(`https://kafka-project-1x9o.onrender.com/api/users/${this.userId}`, formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
            Authorization: `Bearer ${localStorage.getItem('token')}`
          }
        });
        this.updateMessage = 'Profile updated successfully!';
        console.log('Update successful:', response.data);
        localStorage.setItem('profilePictureUrl', response.data.profilePictureUrl);
      } catch (error) {
        this.updateMessage = 'Error updating profile.';
        console.error('Update error:', error);
      }
    }
  },
  created() {
    this.getCurrentUser();
  }
};
</script>

<style scoped>
.profile-edit {
  padding: 20px;
  max-width: 500px;
  margin: 0 auto;
  border: 1px solid #ccc;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.form-group {
  margin-bottom: 15px;
}

label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

input[type="text"],
input[type="file"] {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

button {
  background-color: #4CAF50;
  color: white;
  padding: 10px 15px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

p {
  margin-top: 15px;
  color: green;
}
</style>
