import { getTranslations } from 'next-intl/server';

import { Button } from '@/components/ui/button';
import {
  ArchiveIcon,
  BellIcon,
  CalendarIcon,
  CheckIcon,
  LightningIcon,
  PenIcon,
  TagIcon,
  TerminalIcon,
  ActivityIcon,
} from '@/features/landing/components/PrototypeIcons';

type T = (key: string, values?: Record<string, unknown>) => string;

function FeatureLabels() {
  const labels = [
    { text: 'Investors', tone: 'blue', count: 9 }, // i18n-allow: static Gmail label sample
    { text: 'Customer', tone: 'accent', count: 14 }, // i18n-allow: static Gmail label sample
    { text: 'Receipts', tone: 'amber', count: 218 }, // i18n-allow: static Gmail label sample
    { text: 'Newsletters', tone: 'violet', count: 127 }, // i18n-allow: static Gmail label sample
    { text: 'Deploys', tone: 'green', count: 86 }, // i18n-allow: static Gmail label sample
    { text: 'Recruiters', tone: 'red', count: 31 }, // i18n-allow: static Gmail label sample
  ];

  return (
    <div className="flex flex-wrap gap-2">
      {labels.map((label) => (
        <span key={label.text} className={`zm-pill pill-${label.tone} h-[26px] px-2.5 text-xs`}>
          <span className="size-1.5 rounded-full bg-current opacity-60" />
          {label.text}
          <span className="ml-1 font-mono text-[11px]">{label.count}</span>
        </span>
      ))}
    </div>
  );
}

function FeatureArchive({ t }: { t: T }) {
  const rows = [
    { from: 'LinkedIn', subject: '5 people viewed your profile', reason: t('feat.tester.r2') }, // i18n-allow: static inbox sample
    { from: 'AWS', subject: 'monthly invoice - $42.18', reason: `${t('feat.tester.r1')} < $500` }, // i18n-allow: static inbox sample
    { from: 'Notion', subject: 'weekly digest from your workspace', reason: t('feat.tester.r2') }, // i18n-allow: static inbox sample
  ];

  return (
    <div className="grid gap-1.5">
      {rows.map((row) => (
        <div
          key={row.from}
          className="grid grid-cols-[1fr_auto] items-center gap-2 rounded-md border border-[var(--line)] bg-[var(--bg-subtle)] px-3 py-2 text-xs"
        >
          <span className="min-w-0 truncate">
            <span className="font-medium text-[var(--ink)]">{row.from}</span>
            <span className="text-[var(--text-muted)]"> - {row.subject}</span>
          </span>
          <span className="zm-pill zm-pill-mono h-5 text-[10.5px]">
            <ArchiveIcon size={10} /> {row.reason}
          </span>
        </div>
      ))}
    </div>
  );
}

function FeatureDraft({ t }: { t: T }) {
  return (
    <div className="zm-draft">
      <div className="zm-draft-head">
        <span>
          <PenIcon size={11} /> {t('feat.draft.head')}
        </span>
        <span className="text-[var(--green)]">• {t('feat.draft.ready')}</span>
      </div>
      <div className="zm-draft-meta">
        <div>
          <b>{t('feat.draft.to')}</b> alex@sequoiacap.com
        </div>
        <div>
          <b>{t('feat.draft.subject')}</b> {t('feat.draft.subjectVal')}
        </div>
      </div>
      <div className="zm-draft-body">{t('feat.draft.bodyText')}</div>
      <div className="zm-draft-actions">
        <Button
          type="button"
          variant="outline"
          size="xs"
          className="zm-btn zm-btn-secondary zm-btn-sm h-[26px] text-xs"
        >
          <PenIcon size={11} /> {t('feat.draft.edit')}
        </Button>
        <Button
          type="button"
          variant="ink"
          size="xs"
          className="zm-btn zm-btn-primary zm-btn-sm h-[26px] text-xs"
        >
          {t('feat.draft.open')}
        </Button>
      </div>
    </div>
  );
}

function FeatureTester({ t }: { t: T }) {
  return (
    <div className="zm-tester">
      <div>
        <span className="zm-tester-prompt">{t('feat.tester.prompt')}</span>{' '}
        <span className="zm-tester-input">{t('feat.tester.input')}</span>
        <span className="zm-tester-cursor" />
      </div>
      <div className="mt-2 grid gap-1">
        <div>
          <span className="text-[#6FB3A8]">✓</span> Stripe - {t('feat.tester.r1')} $48 -{' '}
          <b>{t('feat.tester.archive')}</b>
        </div>
        <div>
          <span className="text-[#6FB3A8]">✓</span> LinkedIn - {t('feat.tester.r2')} -{' '}
          <b>{t('feat.tester.archive')}</b>
        </div>
        <div>
          <span className="text-[#6FB3A8]">✓</span> AWS - {t('feat.tester.r3')} $42 -{' '}
          <b>{t('feat.tester.archive')}</b>
        </div>
        <div>
          <span className="text-[#E5C46A]">!</span> Square - {t('feat.tester.r1')} $612 -{' '}
          <b>{t('feat.tester.keep')}</b>{' '}
          <span className="text-[rgba(232,231,224,0.65)]">{t('feat.tester.over')}</span>
        </div>
        <div className="text-[rgba(232,231,224,0.65)]">· {t('feat.tester.more')}</div>
      </div>
    </div>
  );
}

function FeatureAudit({ t }: { t: T }) {
  const rows = [
    ['09:14:22', t('action.archive'), 'stripe.com -> receipts', 'archive'],
    ['09:14:21', `${t('action.label')}+`, 'maya@runway.io -> investors', 'label'],
    ['09:14:18', t('action.draft'), 'alex@sequoiacap.com', 'draft'],
    ['09:14:14', 'skip', 'priya@acme.co - customer', 'skip'],
    ['09:14:10', t('action.archive'), 'linkedin.com -> newsletter', 'archive'],
    ['09:14:07', t('action.archive'), 'notion.so -> newsletter', 'archive'],
  ];

  return (
    <div className="zm-audit">
      {rows.map(([time, action, body, tone]) => (
        <div key={`${time}-${body}`} className="zm-audit-row">
          <span className="zm-audit-time">{time}</span>
          <span>·</span>
          <span>
            <span className={`zm-audit-action ${tone}`}>{action}</span> {body}
          </span>
        </div>
      ))}
    </div>
  );
}

function RulesPreview({ t }: { t: T }) {
  const tabs = [
    ['newsletters', 'action.archive', ArchiveIcon],
    ['investors', 'action.draft', PenIcon],
    ['customer', 'action.alert', BellIcon],
    ['recruiters', 'action.label', TagIcon],
    ['calendar', 'action.draft', CalendarIcon],
  ] as const;
  const rows = [
    ['archive', 'rules.r1.a1', 'amber'],
    ['archive', 'rules.r1.a2', 'amber'],
    ['keep', 'rules.r1.a3', 'green'],
    ['keep', 'rules.r1.a4', 'green'],
  ] as const;

  return (
    <section className="zm-section pt-0" id="rules">
      <div className="zm-container">
        <div className="zm-section-head">
          <span className="zm-eyebrow">
            <span className="zm-dot" />
            {t('rules.eyebrow')}
          </span>
          <h2>{t('rules.title')}</h2>
          <p>{t('rules.desc')}</p>
        </div>
        <div className="zm-rules-shell gap-0">
          <div className="zm-rules-tabs w-full justify-start rounded-none p-0">
            {tabs.map(([id, badgeKey, Icon], index) => (
              <span key={id} className={`zm-rules-tab ${index === 0 ? 'active' : ''}`}>
                <Icon size={13} />
                {t(`rules.tabs.${id}`)}
                <span className="zm-rules-badge">{t(badgeKey)}</span>
              </span>
            ))}
          </div>
          <div className="m-0">
            <div className="zm-rules-body">
              <div className="zm-rule-text-pane">
                <span className="zm-eyebrow">
                  <span className="zm-dot" />
                  {t('rules.ruleN')}
                </span>
                <blockquote>{t('rules.exampleQuote')}</blockquote>
                <div className="mt-5 flex flex-wrap gap-2">
                  <span className="zm-pill zm-pill-mono pill-accent">{t('rules.q.plain')}</span>
                  <span className="zm-pill zm-pill-mono">{t('rules.q.tested')}</span>
                  <span className="zm-pill zm-pill-mono">{t('rules.q.undo')}</span>
                </div>
                <div className="mt-8 border-t border-[var(--line)] pt-6 text-sm leading-relaxed text-[var(--text-muted)]">
                  <div className="mb-2 flex items-center gap-2 font-medium text-[var(--ink)]">
                    <LightningIcon size={14} className="text-[var(--accent)]" />
                    {t('rules.parses.title')}
                  </div>
                  {t('rules.parses.body')}
                </div>
              </div>
              <div className="zm-rule-result-pane">
                <div className="mb-3.5 flex items-center justify-between gap-3">
                  <span className="zm-eyebrow">
                    <span className="zm-dot" />
                    {t('rules.dryrun')}
                  </span>
                  <span className="zm-pill zm-pill-mono pill-green">
                    <CheckIcon size={10} strokeWidth={3} /> {t('rules.matches')}
                  </span>
                </div>
                <div className="grid gap-1.5">
                  {rows.map(([action, key, tone]) => (
                    <div
                      key={key}
                      className="grid grid-cols-[auto_1fr] items-center gap-3 rounded-md border border-[var(--line)] bg-[var(--bg-elevated)] px-3 py-2.5 text-sm"
                    >
                      <span className={`zm-pill pill-${tone} h-[22px] text-[11px]`}>
                        {action === 'archive' ? <ArchiveIcon size={10} /> : <CheckIcon size={10} />}
                        {t(`action.${action}`)}
                      </span>
                      <span className="min-w-0 truncate">{t(key)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default async function Features() {
  const t = (await getTranslations()) as unknown as T;

  return (
    <>
      <section className="zm-section" id="features">
        <div className="zm-container">
          <div className="zm-section-head">
            <span className="zm-eyebrow">
              <span className="zm-dot" />
              {t('feat.eyebrow')}
            </span>
            <h2>{t('feat.title')}</h2>
            <p>{t('feat.desc')}</p>
          </div>

          <div className="zm-feature-grid">
            <article className="zm-feature-card zm-feature-7">
              <div className="zm-feature-head">
                <span className="zm-feature-icon">
                  <TagIcon size={14} />
                </span>
                <h3>{t('feat.labels.title')}</h3>
              </div>
              <p>{t('feat.labels.body')}</p>
              <div className="zm-feature-visual">
                <FeatureLabels />
              </div>
            </article>

            <article className="zm-feature-card zm-feature-5">
              <div className="zm-feature-head">
                <span className="zm-feature-icon">
                  <ArchiveIcon size={14} />
                </span>
                <h3>{t('feat.archive.title')}</h3>
              </div>
              <p>{t('feat.archive.body')}</p>
              <div className="zm-feature-visual">
                <FeatureArchive t={t} />
              </div>
            </article>

            <article className="zm-feature-card zm-feature-5">
              <div className="zm-feature-head">
                <span className="zm-feature-icon">
                  <PenIcon size={14} />
                </span>
                <h3>{t('feat.draft.title')}</h3>
              </div>
              <p>{t('feat.draft.body')}</p>
              <div className="zm-feature-visual">
                <FeatureDraft t={t} />
              </div>
            </article>

            <article className="zm-feature-card zm-feature-7">
              <div className="zm-feature-head">
                <span className="zm-feature-icon">
                  <TerminalIcon size={14} />
                </span>
                <h3>{t('feat.tester.title')}</h3>
              </div>
              <p>{t('feat.tester.body')}</p>
              <div className="zm-feature-visual">
                <FeatureTester t={t} />
              </div>
            </article>

            <article className="zm-feature-card zm-feature-12">
              <div className="zm-feature-head">
                <span className="zm-feature-icon">
                  <ActivityIcon size={14} />
                </span>
                <h3>{t('feat.audit.title')}</h3>
              </div>
              <p className="max-w-xl">{t('feat.audit.body')}</p>
              <div className="zm-feature-visual">
                <FeatureAudit t={t} />
              </div>
            </article>
          </div>
        </div>
      </section>
      <RulesPreview t={t} />
    </>
  );
}
