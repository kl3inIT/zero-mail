import { getTranslations } from 'next-intl/server';

import {
  ArrowUpRightIcon,
  DatabaseIcon,
  FileTextIcon,
  MailIcon,
  PenIcon,
  SendIcon,
  ShieldIcon,
  TagIcon,
  UndoIcon,
  XIcon,
} from '@/features/landing/components/PrototypeIcons';

type TrustPanelVariant = 'full' | 'compact';

export async function TrustPanel({ variant = 'full' }: { variant?: TrustPanelVariant }) {
  const t = await getTranslations();
  const pillars =
    variant === 'compact'
      ? ([
          {
            id: 'p1',
            titleKey: 'trust.authCompact.p1.title',
            bodyKey: 'trust.authCompact.p1.body',
            icon: SendIcon,
          },
          {
            id: 'p2',
            titleKey: 'trust.authCompact.p2.title',
            bodyKey: 'trust.authCompact.p2.body',
            icon: DatabaseIcon,
          },
          {
            id: 'p3',
            titleKey: 'trust.authCompact.p3.title',
            bodyKey: 'trust.authCompact.p3.body',
            icon: UndoIcon,
          },
        ] as const)
      : ([
          { id: 'p1', titleKey: 'trust.p1.title', bodyKey: 'trust.p1.body', icon: SendIcon },
          { id: 'p2', titleKey: 'trust.p2.title', bodyKey: 'trust.p2.body', icon: DatabaseIcon },
          { id: 'p3', titleKey: 'trust.p3.title', bodyKey: 'trust.p3.body', icon: UndoIcon },
        ] as const);
  const permissionIcons = [MailIcon, TagIcon, PenIcon] as const;
  const panelClassName =
    variant === 'compact'
      ? 'zm-trust-panel zm-trust-panel-compact hidden md:block'
      : 'zm-trust-panel hidden md:block';

  return (
    <aside className={panelClassName}>
      <span className="zm-eyebrow">
        <span className="zm-dot" />
        {variant === 'compact' ? t('trust.authCompact.eyebrow') : t('auth.everywhere')}
      </span>
      <h2 className="zm-trust-panel-title">
        {variant === 'compact' ? t('trust.authCompact.title') : t('trust.authTitle')}
      </h2>
      <ul className="zm-trust-list">
        {pillars.map((pillar) => {
          const Icon = pillar.icon;
          return (
            <li key={pillar.id} className="zm-trust-item">
              <span className="zm-trust-ic">
                <Icon size={16} />
              </span>
              <div>
                <h3>{t(pillar.titleKey as never)}</h3>
                <p>{t(pillar.bodyKey as never)}</p>
              </div>
            </li>
          );
        })}
      </ul>
      {variant === 'full' && (
        <>
          <div className="mt-6 flex flex-wrap items-center gap-2 text-xs text-(--text-muted)">
            <span className="inline-flex items-center gap-1">
              <FileTextIcon size={12} /> {t('trust.proof.audit')}
            </span>
            <span>·</span>
            <span className="inline-flex items-center gap-1">
              <ShieldIcon size={12} /> {t('trust.proof.scopes')}
            </span>
            <span>·</span>
            <span className="inline-flex items-center gap-1">
              <ArrowUpRightIcon size={12} /> {t('trust.proof.revoke')}
            </span>
          </div>
          <div className="zm-perm-card">
            <div className="flex items-center justify-between gap-3">
              <h4>{t('permissions.title')}</h4>
              <span className="zm-pill zm-pill-mono">{t('permissions.minimum')}</span>
            </div>
            <p className="mt-2 text-sm leading-relaxed text-(--text-muted)">
              {t('permissions.intro')}
            </p>
            <div className="mt-4 grid gap-3">
              {(['r1', 'r2', 'r3'] as const).map((row, index) => {
                const Icon = permissionIcons[index];
                return (
                  <div key={row} className="grid grid-cols-[22px_1fr] gap-2">
                    <span className="grid size-[22px] place-items-center rounded border border-(--line) bg-(--bg-elevated)">
                      <Icon size={13} />
                    </span>
                    <div>
                      <div className="text-sm font-semibold text-(--ink)">
                        {t(`permissions.${row}.title` as never)}
                      </div>
                      <p className="text-xs leading-relaxed text-(--text-muted)">
                        {t(`permissions.${row}.body` as never)}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="mt-4 flex items-start gap-2 rounded-md border border-dashed border-(--line) bg-(--bg-elevated) p-3 font-mono text-[11.5px] leading-relaxed text-(--text-faint)">
              <XIcon size={12} className="mt-0.5 shrink-0" />
              <span>{t('permissions.notGranted' as never)}</span>
            </div>
          </div>
        </>
      )}
      {variant === 'compact' && (
        <div className="zm-trust-footnote">
          <ShieldIcon size={13} />
          <span>{t('trust.authCompact.footnote')}</span>
        </div>
      )}
    </aside>
  );
}
