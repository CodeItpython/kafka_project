type StorageArea = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

const memoryStore = new Map<string, string>();

const memoryStorage: StorageArea = {
  getItem(key: string) {
    return memoryStore.get(key) ?? null;
  },
  setItem(key: string, value: string) {
    memoryStore.set(key, value);
  },
  removeItem(key: string) {
    memoryStore.delete(key);
  }
};

function getAvailableStorage(): StorageArea {
  if (typeof window === 'undefined') {
    return memoryStorage;
  }

  try {
    const probeKey = '__kafka_talk_storage_probe__';
    window.localStorage.setItem(probeKey, probeKey);
    window.localStorage.removeItem(probeKey);
    return window.localStorage;
  } catch {
    return memoryStorage;
  }
}

const storage = getAvailableStorage();

export const safeStorage = {
  getString(key: string) {
    return storage.getItem(key);
  },
  setString(key: string, value: string) {
    storage.setItem(key, value);
  },
  remove(key: string) {
    storage.removeItem(key);
  }
};
