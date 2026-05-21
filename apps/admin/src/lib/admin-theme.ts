import { useSyncExternalStore } from 'react';

export type AdminTheme = 'light' | 'dark';

const STORAGE_KEY = 'zm-admin-theme';
const EVENT_NAME = 'zm-admin-theme-change';

function readStored(): AdminTheme | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw === 'dark' || raw === 'light' ? raw : null;
  } catch {
    return null;
  }
}

function readSystemPreference(): AdminTheme {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return 'light';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function getAdminTheme(): AdminTheme {
  if (typeof document === 'undefined') return 'light';
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}

export function setAdminTheme(next: AdminTheme): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (next === 'dark') {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
  root.dataset.theme = next;
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    // private mode — best effort
  }
  window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: next }));
}

export function toggleAdminTheme(): AdminTheme {
  const next: AdminTheme = getAdminTheme() === 'dark' ? 'light' : 'dark';
  setAdminTheme(next);
  return next;
}

function subscribe(onStoreChange: () => void): () => void {
  const handler = () => onStoreChange();
  window.addEventListener(EVENT_NAME, handler);
  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) handler();
  });
  return () => {
    window.removeEventListener(EVENT_NAME, handler);
  };
}

export function useAdminTheme(): AdminTheme {
  return useSyncExternalStore(
    subscribe,
    () => getAdminTheme(),
    () => 'light',
  );
}

export const adminThemeInternals = {
  STORAGE_KEY,
  EVENT_NAME,
  readStored,
  readSystemPreference,
};
