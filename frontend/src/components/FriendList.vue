<template>
  <div class="friend-list-container">
    <h2>My Friends</h2>
    <ul class="friend-list">
      <li v-for="friend in friends" :key="friend.id" @click="startChat(friend)" class="friend-item">
        {{ friend.username }}
        <span v-if="newMessages[friend.id]" class="new-message-badge">New!</span>
      </li>
    </ul>
    <div v-if="errorMessage" class="alert-danger">{{ errorMessage }}</div>
  </div>
</template>

<script>
import axios from 'axios';
import Stomp from 'webstomp-client';
import SockJS from 'sockjs-client';

export default {
  name: 'FriendList',
  data() {
    return {
      friends: [],
      errorMessage: '',
      stompClient: null,
      currentUserId: null,
      newMessages: {}, // To track new messages for each friend
    };
  },
  async created() {
    this.currentUserId = localStorage.getItem('userId');
    await this.fetchFriends();
    this.connectWebSocket();
    this.requestNotificationPermission();
  },
  beforeUnmount() {
    this.disconnectWebSocket();
  },
  methods: {
    requestNotificationPermission() {
      if (!('Notification' in window)) {
        console.warn('This browser does not support desktop notification');
      } else if (Notification.permission !== 'granted') {
        Notification.requestPermission().then(permission => {
          if (permission === 'granted') {
            console.log('Notification permission granted.');
          } else {
            console.warn('Notification permission denied.');
          }
        });
      }
    },
    connectWebSocket() {
      const socket = new SockJS('https://kafka-project-1x9o.onrender.com/ws');
      this.stompClient = Stomp.over(socket);
      const token = localStorage.getItem('jwtToken');
      const headers = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      this.stompClient.connect(headers, frame => {
        console.log('Connected to WebSocket for notifications: ' + frame);
        this.stompClient.subscribe(`/user/${this.currentUserId}/queue/notifications`, notification => {
          const message = JSON.parse(notification.body);
          this.newMessages = { ...this.newMessages, [message.senderId]: true };
          
          // Display web notification
          if (Notification.permission === 'granted') {
            const friend = this.friends.find(f => f.id === message.senderId);
            if (friend) {
              new Notification(`New message from ${friend.username}`, {
                body: message.content,
                icon: '/favicon.ico' // You might want a better icon
              });
            }
          }
        });
      }, error => {
        console.error('WebSocket connection error for notifications:', error);
        setTimeout(this.connectWebSocket, 5000);
      });
    },
    disconnectWebSocket() {
      if (this.stompClient && this.stompClient.connected) {
        this.stompClient.disconnect();
        console.log("Disconnected from WebSocket notifications");
      }
    },
    async fetchFriends() {
      try {
        const userId = localStorage.getItem('userId');
        if (!userId) {
          this.errorMessage = 'User not logged in.';
          return;
        }
        const response = await axios.get(`https://kafka-project-1x9o.onrender.com/api/friends/${userId}`);
        this.friends = response.data;
      } catch (error) {
        this.errorMessage = error.response?.data || 'Failed to fetch friends.';
        console.error('Error fetching friends:', error);
      }
    },
    startChat(friend) {
      this.newMessages = { ...this.newMessages, [friend.id]: false }; // Clear notification
      this.$router.push({ name: 'Chat', params: { friendId: friend.id, friendUsername: friend.username } });
    },
  },
};
</script>

<style scoped>
.friend-list-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
  border: 1px solid #eee;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

h2 {
  text-align: center;
  color: #333;
  margin-bottom: 20px;
}

.friend-list {
  list-style: none;
  padding: 0;
}

.friend-item {
  padding: 10px 15px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.friend-item:last-child {
  border-bottom: none;
}

.friend-item:hover {
  background-color: #f9f9f9;
}

.new-message-badge {
  background-color: #28a745;
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 0.7em;
  margin-left: 10px;
}

.alert-danger {
  color: #dc3545;
  background-color: #f8d7da;
  border-color: #f5c6cb;
  padding: 10px;
  border-radius: 5px;
  margin-top: 20px;
  text-align: center;
}
</style>
