type BridgePayload = Record<string, unknown>;

type AndroidBridge = {
  postMessage?: (message: string) => void;
  vibrate?: (duration: number) => void;
};

type WebKitBridge = {
  messageHandlers?: Record<string, { postMessage: (message: BridgePayload) => void } | undefined>;
};

declare global {
  interface Window {
    KafkaTalkBridge?: AndroidBridge;
    webkit?: WebKitBridge;
  }
}

type BridgeEvent =
  | { type: 'screen_opened'; screen: string }
  | { type: 'login_completed'; userId: number; provider: string }
  | { type: 'logout_completed' }
  | { type: 'haptic'; style: 'light' | 'medium' | 'success' | 'warning' };

function postToNative(event: BridgeEvent) {
  if (typeof window === 'undefined') {
    return;
  }

  const iosBridge = window.webkit?.messageHandlers?.KafkaTalkBridge;
  if (iosBridge) {
    iosBridge.postMessage(event);
    return;
  }

  if (window.KafkaTalkBridge?.postMessage) {
    window.KafkaTalkBridge.postMessage(JSON.stringify(event));
  }
}

export const nativeBridge = {
  notifyScreenOpened(screen: string) {
    postToNative({ type: 'screen_opened', screen });
  },
  notifyLoginCompleted(userId: number, provider: string) {
    postToNative({ type: 'login_completed', userId, provider });
  },
  notifyLogoutCompleted() {
    postToNative({ type: 'logout_completed' });
  },
  haptic(style: 'light' | 'medium' | 'success' | 'warning' = 'light') {
    if (typeof window !== 'undefined' && window.KafkaTalkBridge?.vibrate) {
      window.KafkaTalkBridge.vibrate(style === 'medium' ? 24 : 12);
      return;
    }

    postToNative({ type: 'haptic', style });
  }
};
