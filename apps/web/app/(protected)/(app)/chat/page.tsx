import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { ChatWorkspace } from '@/features/chat/components/chat-workspace';

export default function ChatPage() {
  return (
    <Suspense fallback={<LoadingState variant="cards" count={2} />}>
      <ChatWorkspace />
    </Suspense>
  );
}
