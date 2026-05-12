import * as React from 'react';

const MOBILE_BREAKPOINT = 768;

function subscribeToMobileBreakpoint(callback: () => void) {
  const mediaQueryList = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`);
  mediaQueryList.addEventListener('change', callback);
  return () => mediaQueryList.removeEventListener('change', callback);
}

function getMobileSnapshot() {
  return window.innerWidth < MOBILE_BREAKPOINT;
}

function getServerMobileSnapshot() {
  return false;
}

export function useIsMobile() {
  return React.useSyncExternalStore(
    subscribeToMobileBreakpoint,
    getMobileSnapshot,
    getServerMobileSnapshot,
  );
}
