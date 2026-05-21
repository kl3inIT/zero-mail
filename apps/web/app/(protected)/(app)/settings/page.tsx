'use client';

import { SettingsClient } from './SettingsClient';

// Client-component entry. The deferred RSC migration (server-fetch user via
// getCurrentUserCached + hydrate via initialUser) is parked in SettingsClient,
// which already accepts `initialUser` as an optional prop — flip this page
// back to RSC once the e2e harness can mock server-side fetches.
export default function SettingsPage() {
  return <SettingsClient />;
}
