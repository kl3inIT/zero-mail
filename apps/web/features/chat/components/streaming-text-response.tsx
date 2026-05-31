'use client';

import { useEffect, useState } from 'react';

import { Response } from '@/components/ai/response';

const REVEAL_INTERVAL_MS = 16;
const REVEAL_MIN_CHARS_PER_TICK = 4;
const REVEAL_CATCH_UP_DIVISOR = 18;

type StreamingTextResponseProps = {
  text: string;
  revealIncrementally: boolean;
  isStreaming: boolean;
};

export function StreamingTextResponse({
  text,
  revealIncrementally,
  isStreaming,
}: StreamingTextResponseProps) {
  const shouldRevealIncrementally = revealIncrementally && text.length > 0;
  const [visibleText, setVisibleText] = useState(() => (shouldRevealIncrementally ? '' : text));

  useEffect(() => {
    if (!shouldRevealIncrementally || visibleText === text) {
      return;
    }

    const revealTimeout = window.setTimeout(() => {
      setVisibleText((currentText) => {
        const targetText = text;
        if (currentText === targetText) {
          return currentText;
        }
        if (!targetText.startsWith(currentText)) {
          return '';
        }
        const remainingCharacterCount = targetText.length - currentText.length;
        const nextCharacterCount =
          currentText.length +
          Math.max(
            REVEAL_MIN_CHARS_PER_TICK,
            Math.ceil(remainingCharacterCount / REVEAL_CATCH_UP_DIVISOR),
          );
        return targetText.slice(0, nextCharacterCount);
      });
    }, REVEAL_INTERVAL_MS);

    return () => window.clearTimeout(revealTimeout);
  }, [shouldRevealIncrementally, text, visibleText]);

  const responseIsAnimating =
    isStreaming || (shouldRevealIncrementally && visibleText.length < text.length);
  const responseText = shouldRevealIncrementally ? visibleText : text;

  return (
    <Response
      animated={{ duration: 0.12, sep: 'word', stagger: 0.01 }}
      caret="block"
      isAnimating={responseIsAnimating}
    >
      {responseText}
    </Response>
  );
}
