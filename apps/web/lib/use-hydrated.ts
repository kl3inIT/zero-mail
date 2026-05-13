'use client';

import { useSyncExternalStore } from 'react';

function subscribe() {
  return () => {};
}

export function useHydrated(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
}
