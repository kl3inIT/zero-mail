import type { CSSProperties } from 'react';

import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import {
  ArchiveIcon,
  FileTextIcon,
  FilterIcon,
  LockIcon,
  MailIcon,
  PenIcon,
  SearchIcon,
  SendIcon,
  SettingsIcon,
  SparklesIcon,
  StarIcon,
} from '@/features/landing/components/PrototypeIcons';

const rows = [
  {
    from: 'Stripe',
    subject: 'Your weekly payments summary',
    snippet: '$48,210 in 312 charges · 2 disputes opened',
    time: '8:42',
    label: 'Receipts',
    tone: 'amber',
    action: 'archive',
    unread: false,
  },
  {
    from: 'Stripe',
    subject: 'Re: Q3 board prep - slides v3',
    snippet: 'Adding the cohort retention chart you asked for',
    time: '8:31',
    label: 'Investors',
    tone: 'blue',
    action: 'keep',
    unread: true,
  },
  {
    from: 'LinkedIn',
    subject: '5 people viewed your profile this week',
    snippet: 'Including a Director of Eng at Vercel',
    time: '8:14',
    label: 'Newsletters',
    tone: 'violet',
    action: 'archive',
    unread: false,
  },
  {
    from: 'Alex Chen - Sequoia',
    subject: 'Following up after our call',
    snippet: 'Sharing the deck format we discussed',
    time: '7:58',
    label: 'Investors',
    tone: 'blue',
    action: 'draft',
    unread: true,
  },
  {
    from: 'Vercel',
    subject: 'Deploy succeeded · main@a3f21b',
    snippet: 'All 14 checks passed',
    time: '7:30',
    label: 'Deploys',
    tone: 'green',
    action: 'archive',
    unread: false,
  },
  {
    from: 'Notion',
    subject: 'Weekly digest from your workspace',
    snippet: '12 pages updated, 4 comments waiting',
    time: '7:02',
    label: 'Newsletters',
    tone: 'violet',
    action: 'archive',
    unread: false,
  },
  {
    from: 'Priya Anand',
    subject: 'Customer interview notes - Acme',
    snippet: 'Three things stood out from the call',
    time: 'Wed',
    label: 'Customer',
    tone: 'accent',
    action: 'keep',
    unread: false,
  },
] as const;

const suggestionRows = rows.filter((row) => row.action !== 'keep');

const colors = ['#0E5E5A', '#B5731F', '#2C5BA8', '#5E4B8B', '#2F7D5C', '#B0413E'];

function initials(name: string) {
  return name
    .split(/\s+/)
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

function colorFor(name: string) {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return colors[hash % colors.length];
}

export default function InboxPreview() {
  return (
    <div className="zm-gm" aria-label="Gmail triage preview" data-motion="triage-cycle">
      <div className="zm-gm-chrome">
        <div className="zm-gm-dots">
          <span />
          <span />
          <span />
        </div>
        <div className="zm-gm-url">
          <LockIcon size={11} />
          <span>zeromail.com</span>
          <span className="ml-auto inline-flex items-center gap-1 text-[var(--accent)]">
            <SparklesIcon size={11} />
            zeromail
          </span>
        </div>
      </div>

      <div className="zm-gm-app">
        <div className="zm-gm-rail">
          <div className="zm-gm-rail-logo">
            <ZMLogoMark size={14} />
          </div>
          <div className="zm-gm-rail-item active">
            <MailIcon size={17} />
            <span className="zm-gm-count">12</span>
          </div>
          <div className="zm-gm-rail-item">
            <StarIcon size={17} />
          </div>
          <div className="zm-gm-rail-item">
            <SendIcon size={17} />
          </div>
          <div className="zm-gm-rail-item">
            <FileTextIcon size={17} />
          </div>
          <div className="zm-gm-rail-item">
            <ArchiveIcon size={17} />
          </div>
          <div className="flex-1" />
          <div className="zm-gm-rail-item">
            <SettingsIcon size={17} />
          </div>
        </div>

        <div className="zm-gm-main">
          <div className="zm-gm-toolbar">
            <div className="flex min-w-0 items-center gap-2">
              <div className="zm-gm-title">Inbox</div>
              <span className="zm-pill zm-pill-mono">
                <span className="size-1.5 rounded-full bg-[var(--accent)]" />
                Auto-triage on
              </span>
            </div>
            <div className="flex items-center gap-1 text-[var(--text-muted)]">
              <SearchIcon size={14} />
              <FilterIcon size={14} />
              <SettingsIcon size={14} />
            </div>
          </div>

          <div className="zm-gm-banner">
            <SparklesIcon size={14} className="text-[var(--accent)]" />
            <span>
              <b className="text-[var(--accent)]">Zero Mail</b> reviewed 47 messages this morning -
              32 archived, 9 labeled, 6 drafts ready
            </span>
            <span className="text-[var(--accent)]">Review queue</span>
          </div>

          <div className="zm-gm-list">
            {rows.map((row, index) => {
              const motionStep = suggestionRows.findIndex((item) => item === row);
              const motionStyle =
                motionStep >= 0 ? ({ '--zm-motion-step': motionStep } as CSSProperties) : undefined;

              return (
                <div key={`${row.from}-${row.time}`} className="contents">
                  <div
                    className={`zm-gm-row ${row.unread ? 'unread' : ''} ${
                      motionStep >= 0 ? `motion-step-${motionStep}` : ''
                    }`}
                    data-preview-action={row.action !== 'keep' ? row.action : undefined}
                    data-preview-from={row.from}
                    style={motionStyle}
                  >
                    <span
                      aria-hidden="true"
                      className={`zm-gm-check ${index === 1 ? 'checked' : ''}`}
                    />
                    <span
                      aria-hidden="true"
                      className="zm-gm-avatar"
                      style={{ background: colorFor(row.from) }}
                    >
                      {initials(row.from)}
                    </span>
                    <div className="zm-gm-from">{row.from}</div>
                    <div className="zm-gm-content">
                      <span className="zm-gm-subject">{row.subject}</span>
                      <span className="zm-gm-snippet">- {row.snippet}</span>
                    </div>
                    <div className="zm-gm-meta">
                      <span className={`zm-pill pill-${row.tone}`}>{row.label}</span>
                      <span className="zm-gm-time">{row.time}</span>
                    </div>
                  </div>
                  {row.action !== 'keep' && (
                    <div
                      className={`zm-gm-suggestion motion-step-${motionStep}`}
                      data-preview-suggestion={row.action}
                      style={motionStyle}
                    >
                      <div />
                      {row.action === 'draft' ? (
                        <PenIcon size={13} className="text-[var(--accent)]" />
                      ) : (
                        <SparklesIcon size={13} className="text-[var(--accent)]" />
                      )}
                      <div className="zm-gm-suggestion-text">
                        <span className="zm-gm-ai">
                          {row.action === 'draft' ? (
                            <PenIcon size={10} />
                          ) : (
                            <SparklesIcon size={10} />
                          )}{' '}
                          {row.action === 'draft' ? 'Draft ready' : 'AI suggestion'}
                        </span>
                        {row.action === 'draft' ? (
                          <span className="text-[var(--text-muted)]">
                            &quot;Thanks Alex - Wednesday at 2pm works.&quot;
                          </span>
                        ) : (
                          <span>
                            Archive - matches rule{' '}
                            <span className="zm-pill zm-pill-mono pill-accent">
                              &quot;Newsletters &amp; receipts&quot;
                            </span>
                          </span>
                        )}
                        <span className="zm-gm-suggestion-actions">
                          <span className="zm-btn zm-btn-secondary zm-btn-sm h-6 text-[11.5px]">
                            {row.action === 'draft' ? 'Edit' : 'Skip'}
                          </span>
                          <span className="zm-btn zm-btn-primary zm-btn-sm h-6 text-[11.5px]">
                            {row.action === 'draft' ? (
                              'Open draft'
                            ) : (
                              <>
                                <ArchiveIcon size={11} />
                                archive
                              </>
                            )}
                          </span>
                        </span>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
