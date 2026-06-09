'use client';

import { useState } from 'react';
import { Bug, Lightbulb, MessageCircle, Send } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import type { CurrentUser } from '@/features/account/api/account-api';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import type { FeedbackType } from '@/features/support/api/support-api';
import { useSubmitFeedback } from '@/features/support/hooks/use-submit-feedback';

type Tab = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'GENERAL';

const TAB_CONFIG: Record<
  Tab,
  {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    subjectPlaceholder: string;
    messagePlaceholder: string;
  }
> = {
  BUG_REPORT: {
    label: 'Bug Report',
    icon: Bug,
    subjectPlaceholder: 'e.g. Rule is not triggering on matching emails',
    messagePlaceholder: 'Describe what happened, what you expected, and steps to reproduce...',
  },
  FEATURE_REQUEST: {
    label: 'Feature Request',
    icon: Lightbulb,
    subjectPlaceholder: 'e.g. Support for multiple Gmail accounts',
    messagePlaceholder: 'Describe the feature you would like and how it would help you...',
  },
  GENERAL: {
    label: 'General',
    icon: MessageCircle,
    subjectPlaceholder: 'e.g. Question about billing',
    messagePlaceholder: 'What would you like to tell us?',
  },
};

type SupportClientProps = {
  initialUser?: CurrentUser;
};

export function SupportClient({ initialUser }: SupportClientProps) {
  const { data: currentUser } = useCurrentUser(initialUser);
  const submitMutation = useSubmitFeedback();

  const [activeTab, setActiveTab] = useState<Tab>('BUG_REPORT');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [contactEmail, setContactEmail] = useState(currentUser?.email ?? '');

  const config = TAB_CONFIG[activeTab];
  const Icon = config.icon;

  function handleTabChange(value: string) {
    setActiveTab(value as Tab);
    setSubject('');
    setMessage('');
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (submitMutation.isPending) return;
    submitMutation.mutate(
      {
        type: activeTab as FeedbackType,
        subject: subject.trim(),
        message: message.trim(),
        contactEmail: contactEmail.trim(),
      },
      {
        onSuccess: () => {
          setSubject('');
          setMessage('');
        },
      },
    );
  }

  const isValid =
    subject.trim().length > 0 && message.trim().length > 0 && contactEmail.trim().length > 0;

  return (
    <div className="mx-auto max-w-2xl space-y-6 py-8">
      <div>
        <h1 className="text-foreground text-2xl font-semibold tracking-tight">
          Help &amp; Feedback
        </h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Report a bug, request a feature, or get in touch. We read every submission.
        </p>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Send us a message</CardTitle>
          <CardDescription>
            Select the type of feedback and describe what&apos;s on your mind.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-5">
            <Tabs value={activeTab} onValueChange={handleTabChange}>
              <TabsList className="grid w-full grid-cols-3">
                {(Object.entries(TAB_CONFIG) as [Tab, (typeof TAB_CONFIG)[Tab]][]).map(
                  ([key, cfg]) => {
                    const TabIcon = cfg.icon;
                    return (
                      <TabsTrigger key={key} value={key} className="gap-1.5 text-xs">
                        <TabIcon className="size-3.5" />
                        {cfg.label}
                      </TabsTrigger>
                    );
                  },
                )}
              </TabsList>

              {(Object.keys(TAB_CONFIG) as Tab[]).map((key) => (
                <TabsContent key={key} value={key} className="mt-5 space-y-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="subject">Subject</Label>
                    <Input
                      id="subject"
                      value={subject}
                      onChange={(e) => setSubject(e.target.value)}
                      placeholder={TAB_CONFIG[key].subjectPlaceholder}
                      maxLength={200}
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="message">Message</Label>
                    <Textarea
                      id="message"
                      value={message}
                      onChange={(e) => setMessage(e.target.value)}
                      placeholder={TAB_CONFIG[key].messagePlaceholder}
                      rows={6}
                      maxLength={5000}
                      required
                    />
                  </div>
                </TabsContent>
              ))}
            </Tabs>

            <div className="space-y-1.5">
              <Label htmlFor="contactEmail">Your email</Label>
              <Input
                id="contactEmail"
                type="email"
                value={contactEmail}
                onChange={(e) => setContactEmail(e.target.value)}
                placeholder="you@example.com"
                maxLength={320}
                required
              />
              <p className="text-muted-foreground text-xs">
                We will reply here if follow-up is needed.
              </p>
            </div>

            <div className="flex items-center justify-between pt-1">
              <div className="flex items-center gap-2 text-sm">
                <Icon className="text-muted-foreground size-4" />
                <span className="text-muted-foreground">{config.label}</span>
              </div>
              <Button
                type="submit"
                disabled={!isValid || submitMutation.isPending}
                className="gap-1.5"
              >
                <Send className="size-3.5" />
                {submitMutation.isPending ? 'Sending…' : 'Send feedback'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="bg-muted/40">
        <CardContent className="pt-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
            <div className="flex-1 space-y-1">
              <p className="text-foreground text-sm font-medium">Looking for quick answers?</p>
              <p className="text-muted-foreground text-xs">
                Common questions about rules, billing, and Gmail connection are answered in our
                docs.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
