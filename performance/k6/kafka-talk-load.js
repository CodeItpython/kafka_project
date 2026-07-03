import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8890').replace(/\/$/, '');
const PROFILE = __ENV.PROFILE || 'smoke';
const PASSWORD = __ENV.TEST_USER_PASSWORD || 'password123';
const MESSAGE_PREFIX = __ENV.MESSAGE_PREFIX || 'k6-load';
const THINK_TIME_SECONDS = Number.parseFloat(__ENV.THINK_TIME_SECONDS || '1');

const users = parseUsers(__ENV.TEST_USERS);
const profiles = {
  smoke: {
    stages: [
      { duration: '15s', target: 1 },
      { duration: '30s', target: 1 },
      { duration: '10s', target: 0 }
    ],
    thresholds: {
      http_req_failed: ['rate<0.05'],
      http_req_duration: ['p(95)<1200'],
      checks: ['rate>0.95']
    }
  },
  load: {
    stages: [
      { duration: '30s', target: 5 },
      { duration: '2m', target: 10 },
      { duration: '30s', target: 0 }
    ],
    thresholds: {
      http_req_failed: ['rate<0.05'],
      http_req_duration: ['p(95)<1500'],
      checks: ['rate>0.95']
    }
  },
  stress: {
    stages: [
      { duration: '1m', target: 10 },
      { duration: '2m', target: 25 },
      { duration: '1m', target: 40 },
      { duration: '1m', target: 0 }
    ],
    thresholds: {
      http_req_failed: ['rate<0.10'],
      http_req_duration: ['p(95)<2500'],
      checks: ['rate>0.90']
    }
  }
};

export const options = {
  stages: selectedProfile().stages,
  thresholds: selectedProfile().thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  userAgent: 'KafkaTalk-k6/1.0'
};

const loginFailures = new Counter('kafka_talk_login_failures_total');
const chatSendFailures = new Counter('kafka_talk_chat_send_failures_total');
const searchFailures = new Counter('kafka_talk_search_failures_total');
const authenticatedRequests = new Rate('kafka_talk_authenticated_requests_success_rate');
const sendMessageDuration = new Trend('kafka_talk_send_message_duration', true);
const searchDuration = new Trend('kafka_talk_search_duration', true);

export function setup() {
  const sessions = users.map((user) => login(user));
  const authenticatedSessions = sessions.filter((session) => session.token);
  if (authenticatedSessions.length < 2) {
    throw new Error('k6 테스트를 실행하려면 로그인 가능한 테스트 유저가 최소 2명 필요합니다.');
  }
  return {
    sessions: authenticatedSessions,
    baseUrl: BASE_URL
  };
}

export default function (data) {
  const actor = data.sessions[(__VU + __ITER) % data.sessions.length];
  const partner = data.sessions[(__VU + __ITER + 1) % data.sessions.length];
  const headers = authHeaders(actor.token);
  const messageSeed = `${MESSAGE_PREFIX}-${__VU}-${__ITER}-${Date.now()}`;
  let roomId = null;

  group('chat-read-path', () => {
    const heartbeatResponse = http.post(`${data.baseUrl}/api/chat/presence/heartbeat`, null, { headers });
    authenticatedRequests.add(heartbeatResponse.status === 204);
    check(heartbeatResponse, {
      'heartbeat returns 204': (response) => response.status === 204
    });

    const contactsResponse = http.get(`${data.baseUrl}/api/chat/contacts`, { headers });
    authenticatedRequests.add(contactsResponse.status === 200);
    check(contactsResponse, {
      'contacts returns 200': (response) => response.status === 200
    });

    const roomsResponse = http.get(`${data.baseUrl}/api/chat/rooms`, { headers });
    authenticatedRequests.add(roomsResponse.status === 200);
    check(roomsResponse, {
      'rooms returns 200': (response) => response.status === 200
    });
  });

  group('direct-chat-write-path', () => {
    const directRoomResponse = http.post(
      `${data.baseUrl}/api/chat/direct-rooms`,
      JSON.stringify({ partnerEmail: partner.email }),
      { headers }
    );
    authenticatedRequests.add(directRoomResponse.status === 200);
    const directRoomOk = check(directRoomResponse, {
      'direct room returns 200': (response) => response.status === 200,
      'direct room has id': (response) => Boolean(parseJson(response).id)
    });
    if (!directRoomOk) {
      chatSendFailures.add(1);
      return;
    }

    roomId = parseJson(directRoomResponse).id;
    const sendStartedAt = Date.now();
    const sendResponse = http.post(
      `${data.baseUrl}/api/chat/rooms/${roomId}/messages`,
      JSON.stringify({ content: `Kafka Talk k6 message ${messageSeed}` }),
      { headers }
    );
    sendMessageDuration.add(Date.now() - sendStartedAt);
    authenticatedRequests.add(sendResponse.status === 202);
    const sendOk = check(sendResponse, {
      'send message returns 202': (response) => response.status === 202,
      'send message has event id': (response) => Boolean(parseJson(response).messageId)
    });
    if (!sendOk) {
      chatSendFailures.add(1);
    }

    const messagesResponse = http.get(`${data.baseUrl}/api/chat/rooms/${roomId}/messages`, { headers });
    authenticatedRequests.add(messagesResponse.status === 200);
    check(messagesResponse, {
      'messages returns 200': (response) => response.status === 200
    });

    const markReadResponse = http.post(`${data.baseUrl}/api/chat/rooms/${roomId}/read`, null, { headers });
    authenticatedRequests.add(markReadResponse.status === 200);
    check(markReadResponse, {
      'mark read returns 200': (response) => response.status === 200
    });
  });

  group('search-path', () => {
    const query = encodeURIComponent('Kafka');
    const suggestionStartedAt = Date.now();
    const suggestionsResponse = http.get(`${data.baseUrl}/api/chat/suggestions?query=${query}&scope=all`, { headers });
    searchDuration.add(Date.now() - suggestionStartedAt);
    authenticatedRequests.add(suggestionsResponse.status === 200);
    const suggestionsOk = check(suggestionsResponse, {
      'suggestions returns 200': (response) => response.status === 200
    });
    if (!suggestionsOk) {
      searchFailures.add(1);
    }

    const searchStartedAt = Date.now();
    const searchResponse = http.get(`${data.baseUrl}/api/chat/messages/search?query=${query}`, { headers });
    searchDuration.add(Date.now() - searchStartedAt);
    authenticatedRequests.add(searchResponse.status === 200);
    const searchOk = check(searchResponse, {
      'message search returns 200': (response) => response.status === 200
    });
    if (!searchOk) {
      searchFailures.add(1);
    }
  });

  if (roomId) {
    group('presence-path', () => {
      const typingResponse = http.post(
        `${data.baseUrl}/api/chat/rooms/${roomId}/typing`,
        JSON.stringify({ typing: true }),
        { headers }
      );
      authenticatedRequests.add(typingResponse.status === 204);
      check(typingResponse, {
        'typing true returns 204': (response) => response.status === 204
      });

      const presenceResponse = http.get(`${data.baseUrl}/api/chat/rooms/${roomId}/presence`, { headers });
      authenticatedRequests.add(presenceResponse.status === 200);
      check(presenceResponse, {
        'room presence returns 200': (response) => response.status === 200
      });

      const typingStopResponse = http.post(
        `${data.baseUrl}/api/chat/rooms/${roomId}/typing`,
        JSON.stringify({ typing: false }),
        { headers }
      );
      authenticatedRequests.add(typingStopResponse.status === 204);
      check(typingStopResponse, {
        'typing false returns 204': (response) => response.status === 204
      });
    });
  }

  sleep(Number.isFinite(THINK_TIME_SECONDS) ? THINK_TIME_SECONDS : 1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    'performance/k6/results/summary.json': JSON.stringify(data, null, 2)
  };
}

function login(user) {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json'
      }
    }
  );
  const ok = check(response, {
    [`login succeeds for ${user.email}`]: (loginResponse) => loginResponse.status === 200,
    [`token exists for ${user.email}`]: (loginResponse) => Boolean(parseJson(loginResponse).accessToken)
  });
  if (!ok) {
    loginFailures.add(1);
  }
  return {
    email: user.email,
    token: parseJson(response).accessToken
  };
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json'
  };
}

function parseUsers(rawUsers) {
  if (!rawUsers) {
    return [
      { email: 'user@example.com', password: PASSWORD },
      { email: 'minji@example.com', password: PASSWORD },
      { email: 'junho@example.com', password: PASSWORD },
      { email: 'seoyeon@example.com', password: PASSWORD },
      { email: 'hyejin@example.com', password: PASSWORD }
    ];
  }
  try {
    const parsedUsers = JSON.parse(rawUsers);
    if (!Array.isArray(parsedUsers)) {
      throw new Error('TEST_USERS must be a JSON array.');
    }
    return parsedUsers.map((user) => ({
      email: String(user.email),
      password: String(user.password || PASSWORD)
    }));
  } catch (error) {
    throw new Error(`TEST_USERS JSON 파싱 실패: ${error.message}`);
  }
}

function selectedProfile() {
  if (!profiles[PROFILE]) {
    throw new Error(`지원하지 않는 k6 PROFILE입니다: ${PROFILE}. smoke, load, stress 중 하나를 사용하세요.`);
  }
  return profiles[PROFILE];
}

function parseJson(response) {
  try {
    return response.json();
  } catch (error) {
    return {};
  }
}

function textSummary(data) {
  const metrics = data.metrics;
  const checksRate = metricValue(metrics.checks, 'rate');
  const failedRate = metricValue(metrics.http_req_failed, 'rate');
  const p95 = metricValue(metrics.http_req_duration, 'p(95)');
  const p99 = metricValue(metrics.http_req_duration, 'p(99)');
  const iterations = metricValue(metrics.iterations, 'count');
  const requests = metricValue(metrics.http_reqs, 'count');
  return `
Kafka Talk k6 load test
=======================
profile: ${PROFILE}
baseUrl: ${BASE_URL}
iterations: ${iterations}
http requests: ${requests}
check success rate: ${toPercent(checksRate)}
http failure rate: ${toPercent(failedRate)}
http duration p95: ${toMillis(p95)}
http duration p99: ${toMillis(p99)}

Full JSON summary: performance/k6/results/summary.json
`;
}

function metricValue(metric, key) {
  if (!metric || !metric.values || metric.values[key] === undefined) {
    return 0;
  }
  return metric.values[key];
}

function toPercent(value) {
  return `${(value * 100).toFixed(2)}%`;
}

function toMillis(value) {
  return `${value.toFixed(2)}ms`;
}
