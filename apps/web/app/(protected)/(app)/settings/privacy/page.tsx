import { PrivacySections } from '@/features/privacy/components/PrivacySections';

export default async function SettingsPrivacyPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <PrivacySections />
      </div>
    </div>
  );
}
