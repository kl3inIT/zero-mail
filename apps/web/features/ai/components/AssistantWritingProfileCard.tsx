'use client';

import { useTranslations } from 'next-intl';
import { Sparkles } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import {
  useAssistantSettings,
  useUpdateAssistantSettings,
} from '@/features/ai/hooks/useAssistantSettings';

const MAX_PERSONAL_INSTRUCTIONS_LENGTH = 2_000;
const MAX_WRITING_STYLE_LENGTH = 2_000;
const LANGUAGE_AUTO = 'auto';

export function AssistantWritingProfileCard() {
  const t = useTranslations();
  const settingsQuery = useAssistantSettings();
  const updateMutation = useUpdateAssistantSettings();

  if (settingsQuery.isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Sparkles className="text-muted-foreground size-4" aria-hidden="true" />
            {t('ai.writingProfile.title')}
          </CardTitle>
          <CardDescription>{t('ai.writingProfile.description')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-9 w-40" />
        </CardContent>
      </Card>
    );
  }

  return (
    <AssistantWritingProfileForm
      initialPersonalInstructions={settingsQuery.data?.personalInstructions ?? ''}
      initialWritingStyle={settingsQuery.data?.writingStyle ?? ''}
      initialAiOutputLanguage={settingsQuery.data?.aiOutputLanguage ?? LANGUAGE_AUTO}
      saving={updateMutation.isPending}
      onSave={(payload) =>
        updateMutation.mutate(payload, {
          onSuccess: () => toast.success(t('ai.writingProfile.saved')),
          onError: () => toast.error(t('ai.writingProfile.saveFailed')),
        })
      }
    />
  );
}

function AssistantWritingProfileForm({
  initialPersonalInstructions,
  initialWritingStyle,
  initialAiOutputLanguage,
  saving,
  onSave,
}: {
  initialPersonalInstructions: string;
  initialWritingStyle: string;
  initialAiOutputLanguage: string;
  saving: boolean;
  onSave: (payload: {
    personalInstructions: string | null;
    writingStyle: string | null;
    aiOutputLanguage: string | null;
  }) => void;
}) {
  const t = useTranslations();
  const [personalInstructions, setPersonalInstructions] = useState(initialPersonalInstructions);
  const [writingStyle, setWritingStyle] = useState(initialWritingStyle);
  const [aiOutputLanguage, setAiOutputLanguage] = useState(initialAiOutputLanguage);

  const personalInstructionsTrimmedLength = personalInstructions.trim().length;
  const writingStyleTrimmedLength = writingStyle.trim().length;
  const personalOverLimit = personalInstructionsTrimmedLength > MAX_PERSONAL_INSTRUCTIONS_LENGTH;
  const styleOverLimit = writingStyleTrimmedLength > MAX_WRITING_STYLE_LENGTH;
  const submitDisabled = saving || personalOverLimit || styleOverLimit;

  function handleSubmit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    if (submitDisabled) return;
    onSave({
      personalInstructions: personalInstructions.trim() || null,
      writingStyle: writingStyle.trim() || null,
      aiOutputLanguage: aiOutputLanguage === LANGUAGE_AUTO ? null : aiOutputLanguage,
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="text-muted-foreground size-4" aria-hidden="true" />
          {t('ai.writingProfile.title')}
        </CardTitle>
        <CardDescription>{t('ai.writingProfile.description')}</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="ai-personal-instructions">
              {t('ai.writingProfile.personalInstructions.label')}
            </Label>
            <Textarea
              id="ai-personal-instructions"
              value={personalInstructions}
              onChange={(changeEvent) => setPersonalInstructions(changeEvent.target.value)}
              placeholder={t('ai.writingProfile.personalInstructions.placeholder')}
              rows={4}
              maxLength={MAX_PERSONAL_INSTRUCTIONS_LENGTH}
              aria-describedby="ai-personal-instructions-help"
              data-testid="assistant-personal-instructions-textarea"
            />
            <div className="flex items-center justify-between gap-2">
              <p id="ai-personal-instructions-help" className="text-muted-foreground text-xs">
                {t('ai.writingProfile.personalInstructions.help')}
              </p>
              <span className="text-muted-foreground text-xs tabular-nums">
                {t('ai.writingProfile.charCount', {
                  current: personalInstructionsTrimmedLength,
                  max: MAX_PERSONAL_INSTRUCTIONS_LENGTH,
                })}
              </span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="ai-writing-style">{t('ai.writingProfile.writingStyle.label')}</Label>
            <Textarea
              id="ai-writing-style"
              value={writingStyle}
              onChange={(changeEvent) => setWritingStyle(changeEvent.target.value)}
              placeholder={t('ai.writingProfile.writingStyle.placeholder')}
              rows={4}
              maxLength={MAX_WRITING_STYLE_LENGTH}
              aria-describedby="ai-writing-style-help"
              data-testid="assistant-writing-style-textarea"
            />
            <div className="flex items-center justify-between gap-2">
              <p id="ai-writing-style-help" className="text-muted-foreground text-xs">
                {t('ai.writingProfile.writingStyle.help')}
              </p>
              <span className="text-muted-foreground text-xs tabular-nums">
                {t('ai.writingProfile.charCount', {
                  current: writingStyleTrimmedLength,
                  max: MAX_WRITING_STYLE_LENGTH,
                })}
              </span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="ai-output-language">{t('ai.writingProfile.language.label')}</Label>
            <Select
              value={aiOutputLanguage}
              onValueChange={(nextLanguage) => {
                if (typeof nextLanguage === 'string') setAiOutputLanguage(nextLanguage);
              }}
            >
              <SelectTrigger id="ai-output-language" className="w-full sm:w-[280px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={LANGUAGE_AUTO}>
                  {t('ai.writingProfile.language.auto')}
                </SelectItem>
                <SelectItem value="vi">{t('ai.writingProfile.language.vi')}</SelectItem>
                <SelectItem value="en">{t('ai.writingProfile.language.en')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
        <CardFooter>
          <Button
            type="submit"
            disabled={submitDisabled}
            data-testid="assistant-writing-profile-save"
          >
            {saving ? t('ai.writingProfile.saving') : t('ai.writingProfile.save')}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
