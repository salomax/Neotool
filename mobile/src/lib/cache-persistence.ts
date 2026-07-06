import type { InMemoryCache, NormalizedCacheObject } from '@apollo/client';
import AsyncStorage from '@react-native-async-storage/async-storage';

const APOLLO_CACHE_STORAGE_KEY = 'apollo-cache';
const PERSIST_DEBOUNCE_MS = 250;

type PersistableCache = InMemoryCache & {
  broadcastWatches: (...args: readonly unknown[]) => void;
};

async function persistCacheSnapshot(cache: InMemoryCache): Promise<void> {
  const snapshot = cache.extract();
  await AsyncStorage.setItem(APOLLO_CACHE_STORAGE_KEY, JSON.stringify(snapshot));
}

export async function restorePersistedCache(cache: InMemoryCache): Promise<void> {
  const serializedCache = await AsyncStorage.getItem(APOLLO_CACHE_STORAGE_KEY);

  if (!serializedCache) {
    return;
  }

  try {
    cache.restore(JSON.parse(serializedCache) as NormalizedCacheObject);
  } catch (error) {
    console.error('Error restoring persisted Apollo cache:', error);
    await AsyncStorage.removeItem(APOLLO_CACHE_STORAGE_KEY);
  }
}

export function attachCachePersistence(cache: InMemoryCache): () => void {
  const persistableCache = cache as PersistableCache;
  const originalBroadcastWatches = persistableCache.broadcastWatches.bind(cache);
  let persistTimeout: ReturnType<typeof setTimeout> | null = null;
  let isDisposed = false;

  const schedulePersistence = () => {
    if (isDisposed) {
      return;
    }

    if (persistTimeout) {
      clearTimeout(persistTimeout);
    }

    persistTimeout = setTimeout(() => {
      persistTimeout = null;
      void persistCacheSnapshot(cache).catch((error) => {
        console.error('Error persisting Apollo cache:', error);
      });
    }, PERSIST_DEBOUNCE_MS);
  };

  persistableCache.broadcastWatches = (...args: readonly unknown[]) => {
    originalBroadcastWatches(...args);
    schedulePersistence();
  };

  return () => {
    isDisposed = true;

    if (persistTimeout) {
      clearTimeout(persistTimeout);
      persistTimeout = null;
    }

    persistableCache.broadcastWatches = originalBroadcastWatches;
  };
}

export async function clearPersistedCache(): Promise<void> {
  await AsyncStorage.removeItem(APOLLO_CACHE_STORAGE_KEY);
}
