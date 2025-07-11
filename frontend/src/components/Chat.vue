<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>Chat with {{ friendUsername }}</h2>
      <p>You are chatting as: {{ currentUsername }}</p>
      <button @click="goBack" class="back-button">Back to Friends</button>
    </div>
    <div class="messages" ref="messageContainer">
      <div v-for="message in messages" :key="message.id" :class="['message-item', message.senderId == currentUserId ? 'sent' : 'received']">
        <div class="message-avatar">
          <img v-if="message.senderId == currentUserId && currentUserProfilePicture" :src="currentUserProfilePicture" alt="My Profile" class="profile-pic" />
          <img v-else-if="message.senderId != currentUserId && friendProfilePicture" :src="friendProfilePicture" alt="Friend Profile" class="profile-pic" />
          <!-- Fallback for no profile picture -->
          <div v-else class="profile-pic-placeholder">{{ message.senderId == currentUserId ? currentUsername.charAt(0) : friendUsername.charAt(0) }}</div>
        </div>
        <div class="message-content-wrapper">
          <div class="message-sender">{{ message.senderId == currentUserId ? currentUsername : friendUsername }}</div>
          <div class="message-content">
            <img v-if="message.type === 'IMAGE'" :src="message.imageUrl" alt="Image" class="chat-image" />
            <span v-else>{{ message.content }}</span>
          </div>
          <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        </div>
      </div>
    </div>
    <div class="message-input">
      <input type="file" @change="handleImageUpload" accept="image/*" />
      <input type="text" v-model="newMessage" @keyup.enter="sendMessage" placeholder="Type a message..." />
      <button @click="sendMessage">Send</button>
    </div>
  </div>
</template>

<script>
import Stomp from 'webstomp-client';
import SockJS from 'sockjs-client';
import axios from 'axios';

export default {
  name: 'Chat',
  props: {
    friendId: { type: [String, Number], required: true },
    friendUsername: { type: String, required: true },
  },
  data() {
    return {
      currentUserId: null,
      currentUsername: null,
      stompClient: null,
      messages: [],
      newMessage: '',
      currentUserProfilePicture: null,
      friendProfilePicture: null,
    };
  },
  created() {
    this.currentUserId = localStorage.getItem('userId');
    this.currentUsername = localStorage.getItem('username');
    console.log('Chat.vue created:');
    console.log('  currentUserId:', this.currentUserId);
    console.log('  currentUsername:', this.currentUsername);
    console.log('  friendId:', this.friendId);
    console.log('  friendUsername:', this.friendUsername);
  },
  mounted() {
    this.connectWebSocket();
    this.fetchUserProfile(this.currentUserId, 'currentUser');
    this.fetchUserProfile(this.friendId, 'friend');
    this.currentUserProfilePicture = localStorage.getItem('profilePictureUrl');
  },
  beforeUnmount() {
    this.disconnectWebSocket();
  },
  watch: {
    currentUserProfilePicture(newVal, oldVal) {
      // React to changes in currentUserProfilePicture
      console.log('currentUserProfilePicture changed:', oldVal, '->', newVal);
    }
  },
  methods: {
    async fetchUserProfile(userId, type) {
      try {
        const response = await axios.get(`https://kafka-project-1x9o.onrender.com/api/users/${userId}`, {
          headers: {
            Authorization: `Bearer ${localStorage.getItem('jwtToken')}`
          }
        });
        if (type === 'currentUser') {
          this.currentUserProfilePicture = response.data.profilePictureUrl;
          localStorage.setItem('profilePictureUrl', response.data.profilePictureUrl);
        } else if (type === 'friend') {
          this.friendProfilePicture = response.data.profilePictureUrl;
        }
      } catch (error) {
        console.error(`Error fetching ${type} profile:`, error);
      }
    },
    connectWebSocket() {
      const socket = new SockJS('https://kafka-project-1x9o.onrender.com/ws');
      this.stompClient = Stomp.over(socket);
      const token = localStorage.getItem('jwtToken'); // Get JWT token from localStorage
      const headers = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`; // Add Authorization header
      }

      this.stompClient.connect(headers, frame => {
        console.log('Connected: ' + frame);
        // Subscribe to personal message queue
        this.stompClient.subscribe(`/user/${this.currentUserId}/queue/messages`, message => {
          const receivedMessage = JSON.parse(message.body);
          this.messages.push(receivedMessage);
          this.$nextTick(() => {
            this.scrollToBottom();
          });
        });

        // Subscribe to chat history queue
        this.stompClient.subscribe(`/user/${this.currentUserId}/queue/history`, message => {
          this.messages = JSON.parse(message.body);
          this.$nextTick(() => {
            this.scrollToBottom();
          });
        });

        // Request chat history
        this.requestChatHistory();

      }, error => {
        console.error('WebSocket connection error:', error);
        setTimeout(this.connectWebSocket, 5000); // Reconnect after 5 seconds
      });
    },
    disconnectWebSocket() {
      if (this.stompClient && this.stompClient.connected) {
        this.stompClient.disconnect();
        console.log("Disconnected");
      }
    },
    handleImageUpload(event) {
      const file = event.target.files[0];
      if (file) {
        this.uploadImage(file);
      }
    },
    async uploadImage(file) {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('senderId', this.currentUserId);
      formData.append('receiverId', this.friendId);

      try {
        const token = localStorage.getItem('jwtToken');
        await axios.post('https://kafka-project-1x9o.onrender.com/upload-image', formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
            'Authorization': `Bearer ${token}`
          }
        });
        console.log('Image uploaded successfully');
      } catch (error) {
        console.error('Error uploading image:', error);
      }
    },
    sendMessage() {
      const contentToSend = this.newMessage.trim();
      if (contentToSend === '') {
        return; // Don't send empty text messages
      }

      const chatMessage = {
        senderId: Number(this.currentUserId),
        receiverId: Number(this.friendId),
        content: contentToSend,
        type: 'CHAT',
      };

      if (this.stompClient && this.stompClient.connected) {
        console.log('Sending chat message:', chatMessage);
        this.stompClient.send("/app/chat.sendMessage", JSON.stringify(chatMessage), {});
        this.newMessage = '';
      } else {
        console.error("WebSocket not connected.");
      }
    },
    requestChatHistory() {
      if (this.stompClient && this.stompClient.connected) {
        const historyRequest = {
          senderId: Number(this.currentUserId),
          receiverId: Number(this.friendId),
        };
        this.stompClient.send("/app/chat.getHistory", JSON.stringify(historyRequest), {});
      }
    },
    formatTime(timestamp) {
      if (!timestamp) return '';
      const date = new Date(timestamp);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    },
    scrollToBottom() {
      const container = this.$refs.messageContainer;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    },
    goBack() {
      this.$router.push('/friends');
    },
  },
};
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 800px;
  margin: 0 auto;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}

.chat-header {
  padding: 15px;
  background-color: #f8f8f8;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h2 {
  margin: 0;
  font-size: 1.2em;
}

.back-button {
  background-color: #007bff;
  color: white;
  border: none;
  padding: 8px 12px;
  border-radius: 5px;
  cursor: pointer;
}

.messages {
  flex-grow: 1;
  padding: 15px;
  overflow-y: auto;
  background-color: #f0f2f5;
}

.message-item {
  display: flex;
  align-items: flex-start; /* Align items to the top */
  margin-bottom: 10px;
}

.message-item.sent {
  flex-direction: row-reverse; /* Reverse order for sent messages */
  justify-content: flex-start;
}

.message-item.received {
  flex-direction: row;
  justify-content: flex-start;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 10px;
  flex-shrink: 0; /* Prevent shrinking */
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #ccc; /* Placeholder background */
  color: #fff;
  font-weight: bold;
}

.message-item.sent .message-avatar {
  margin-right: 0;
  margin-left: 10px; /* Adjust margin for sent messages */
}

.profile-pic {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-pic-placeholder {
  font-size: 1.2em;
  text-transform: uppercase;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  max-width: calc(100% - 50px); /* Adjust max-width considering avatar */
}

.message-item.sent .message-content-wrapper {
  align-items: flex-end; /* Align children (sender, content, time) to the right */
}

.message-item.received .message-content-wrapper {
  align-items: flex-start; /* Align children (sender, content, time) to the left */
}

.message-content {
  padding: 8px 12px;
  border-radius: 15px;
  word-wrap: break-word;
  display: inline-block; /* Ensures the bubble shrinks to content size */
}

.message-item.sent .message-content {
  background-color: #dcf8c6;
}

.message-item.received .message-content {
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
}

.message-sender {
  font-size: 0.8em;
  color: #555;
  margin-bottom: 2px;
}

.message-time {
  font-size: 0.75em;
  color: #888;
  margin-top: 2px;
}

.message-input {
  display: flex;
  padding: 15px;
  border-top: 1px solid #eee;
  background-color: #f8f8f8;
}

.message-input input {
  flex-grow: 1;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 20px;
  margin-right: 10px;
}

.message-input button {
  background-color: #007bff;
  color: white;
  border: none;
  padding: 10px 15px;
  border-radius: 20px;
  cursor: pointer;
}

.chat-image {
  max-width: 240px;
  height: auto;
  border-radius: 8px;
  display: block;
  margin: 4px 0;
}

.message-content:has(img.chat-image) {
  padding: 4px;
  background-color: transparent;
}

/* Fallback for browsers that do not support :has() selector.
   To use, wrap .message-content in a div with class="image-wrapper" in the template.
*/
.image-wrapper {
  padding: 4px;
  background-color: transparent;
}
</style>