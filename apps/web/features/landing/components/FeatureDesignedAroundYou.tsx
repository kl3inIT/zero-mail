import { getTranslations } from 'next-intl/server';

export async function FeatureDesignedAroundYou() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container mb-24">
      <div className="mb-16 text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          {t('sec6.title')}
        </h2>
        <p className="mx-auto max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          {t('sec6.body')}
        </p>
      </div>

      <div className="mx-auto max-w-7xl rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-4 shadow-sm md:p-6">
        <div className="grid grid-cols-1 gap-4 md:gap-6 lg:grid-cols-3">
          {/* Col 1 */}
          <div className="flex min-h-[460px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-5 flex size-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                <svg className="size-4" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M4 20h3V10H4v10zm6 0h3V4h-3v16zm6 0h3v-8h-3v8z" />
                </svg>
              </div>
              <h3 className="mb-3 pr-4 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec6.col1.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec6.col1.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-end justify-center px-6 pt-8 pb-0">
              <div className="relative w-full translate-y-3 overflow-hidden rounded-t-[20px] border border-b-0 border-gray-200 bg-white shadow-[0_-4px_20px_rgba(0,0,0,0.04)] transition-transform duration-500 hover:translate-y-1">
                <div className="p-5">
                  <h4 className="mb-4 text-[15px] font-semibold text-gray-600">
                    {t('sec6.col1.mockHeading')}
                  </h4>
                  <div className="mb-2 flex justify-between text-[11px] font-semibold tracking-wider text-gray-600 uppercase">
                    <span>{t('sec6.col1.mockSenderHeader')}</span>
                    <span>{t('sec6.col1.mockCountHeader')}</span>
                  </div>
                  <div className="flex flex-col gap-2">
                    <div className="relative z-10 flex items-center justify-between text-sm">
                      <div className="absolute inset-0 -z-10 w-[85%] rounded-md bg-[#0068FF]/15" />
                      <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                        Stripe{' '}
                        <span className="font-normal text-gray-400">&lt;notifications…&gt;</span>
                      </span>
                      <span className="px-2 font-semibold text-gray-600">64</span>
                    </div>
                    <div className="relative z-10 flex items-center justify-between text-sm">
                      <div className="absolute inset-0 -z-10 w-[70%] rounded-md bg-[#0068FF]/15" />
                      <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                        Sentry{' '}
                        <span className="font-normal text-gray-400">&lt;noreply@...&gt;</span>
                      </span>
                      <span className="px-2 font-semibold text-gray-600">45</span>
                    </div>
                    <div className="relative z-10 flex items-center justify-between text-sm">
                      <div className="absolute inset-0 -z-10 w-[50%] rounded-md bg-[#0068FF]/15" />
                      <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                        beehiiv <span className="font-normal text-gray-400">&lt;buzz@...&gt;</span>
                      </span>
                      <span className="px-2 font-semibold text-gray-600">32</span>
                    </div>
                    <div className="relative z-10 flex items-center justify-between text-sm opacity-50">
                      <div className="absolute inset-0 -z-10 w-[30%] rounded-md bg-[#0068FF]/10" />
                      <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                        Mailgun <span className="font-normal text-gray-400">&lt;report…&gt;</span>
                      </span>
                      <span className="px-2 font-semibold text-gray-600">24</span>
                    </div>
                  </div>
                </div>
                <div className="pointer-events-none absolute right-0 bottom-0 left-0 h-12 bg-gradient-to-t from-white to-transparent" />
              </div>
            </div>
          </div>

          {/* Col 2 */}
          <div className="flex min-h-[460px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-5 flex size-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                <svg
                  className="size-4"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"
                  />
                </svg>
              </div>
              <h3 className="mb-3 pr-4 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec6.col2.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec6.col2.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-center justify-center overflow-hidden p-8">
              <div className="grid grid-cols-3 gap-5">
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <svg className="size-7" viewBox="0 0 24 24" fill="none">
                    <path d="M11.636 1.708l-6.818 2.046v16.492l6.818 2.046V1.708z" fill="#0078D4" />
                    <path d="M4.818 3.754H1.364v16.492h3.454V3.754z" fill="#50E6FF" />
                    <path d="M11.636 1.708h11V22.29h-11V1.708z" fill="#28A8EA" />
                    <path
                      d="M16 16.602l-1.282-1.364h1.75c.99 0 1.56-.516 1.56-1.362V9.824c0-.847-.57-1.364-1.56-1.364h-1.75l1.282-1.364h1.96c1.613 0 2.802.99 2.802 2.656v4.204c0 1.666-1.189 2.646-2.802 2.646H16z"
                      fill="#fff"
                    />
                  </svg>
                </div>
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <svg className="size-7" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M17 2H7C4.24 2 2 4.24 2 7V17C2 19.76 4.24 22 7 22H17C19.76 22 22 19.76 22 17V7C22 4.24 19.76 2 17 2Z"
                      fill="white"
                    />
                    <path
                      d="M17 2H7C4.24 2 2 4.24 2 7V17C2 19.76 4.24 22 7 22H17C19.76 22 22 19.76 22 17V7C22 4.24 19.76 2 17 2Z"
                      fill="#F4F4F4"
                    />
                    <path
                      d="M16.5 22H7.5C4.46 22 2 19.54 2 16.5V7.5C2 4.46 4.46 2 7.5 2H16.5C19.54 2 22 4.46 22 7.5V16.5C22 19.54 19.54 22 16.5 22Z"
                      fill="#4285F4"
                    />
                    <rect x="5.5" y="5.5" width="13" height="13" rx="1.5" fill="white" />
                    <path
                      d="M12.5 15.5V10.5H10L9 11.25M15.5 13H12"
                      stroke="#4285F4"
                      strokeWidth="1.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    <path
                      d="M12 11.5H13.5V13.5C13.5 14.6 12.6 15.5 11.5 15.5H10"
                      stroke="#34A853"
                      strokeWidth="1.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    <path
                      d="M10 11.5H11.5V9.5C11.5 8.4 12.4 7.5 13.5 7.5H15"
                      stroke="#EA4335"
                      strokeWidth="1.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <svg className="size-7" viewBox="0 0 24 24" fill="black">
                    <path d="M4 4h4l8 11V4h4v16h-4L4 9v11H0V4h4z" />
                  </svg>
                </div>
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <svg className="size-7" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M24 5.457v13.909c0 .904-.732 1.636-1.636 1.636h-3.819V11.73L12 16.64l-6.545-4.91v9.273H1.636A1.636 1.636 0 0 1 0 19.366V5.457c0-2.023 2.309-3.178 3.927-1.964L5.455 4.64 12 9.548l6.545-4.91 1.528-1.145C21.69 2.28 24 3.434 24 5.457z"
                      fill="#4285F4"
                    />
                    <path
                      d="M18.545 11.73V21H22.364c.904 0 1.636-.732 1.636-1.636V5.457c0-.494-.176-.948-.473-1.303L18.545 11.73z"
                      fill="#34A853"
                    />
                    <path
                      d="M5.455 11.73v9.27H1.636C.732 21 0 20.268 0 19.364V5.457c0-.494.176-.948.473-1.303L5.455 11.73z"
                      fill="#FBBC04"
                    />
                    <path
                      d="M12 9.548 5.455 4.64l-1.528-1.145c-.328-.246-.723-.393-1.146-.393-.896 0-1.64.71-1.677 1.597L12 16.64l10.896-11.94c-.038-.888-.781-1.597-1.677-1.597-.423 0-.818.147-1.146.393L18.545 4.64 12 9.548z"
                      fill="#EA4335"
                    />
                  </svg>
                </div>
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <span className="text-[17px] font-extrabold tracking-tighter text-[#635BFF]">
                    stripe
                  </span>
                </div>
                <div className="flex size-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                  <div className="flex gap-1.5">
                    <div className="h-4 w-1.5 rotate-45 rounded-full bg-[#00C875]" />
                    <div className="-mt-1.5 h-4 w-1.5 rotate-45 rounded-full bg-[#FF9900]" />
                    <div className="-mt-3 h-4 w-1.5 rotate-45 rounded-full bg-[#E2445C]" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Col 3 */}
          <div className="flex min-h-[460px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-5 flex size-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                <svg className="size-4" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z" />
                </svg>
              </div>
              <h3 className="mb-3 pr-4 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec6.col3.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec6.col3.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-end justify-center px-6 pt-8 pb-6">
              <div className="flex w-full translate-y-2 flex-col overflow-hidden rounded-[16px] border border-gray-200 bg-white shadow-[0_4px_20px_rgba(0,0,0,0.04)] transition-transform duration-500 hover:translate-y-0">
                <div className="relative flex flex-1 flex-col gap-3 p-5">
                  <div className="flex items-start gap-3 opacity-90">
                    <div className="mt-2 size-1.5 shrink-0 rounded-full bg-gray-400" />
                    <p className="text-sm leading-snug text-gray-600">{t('sec6.col3.rule1')}</p>
                  </div>
                  <div className="flex items-start gap-3 opacity-80">
                    <div className="mt-2 size-1.5 shrink-0 rounded-full bg-gray-400" />
                    <p className="text-sm leading-snug text-gray-600">{t('sec6.col3.rule2')}</p>
                  </div>
                  <div className="flex items-start gap-3 opacity-60">
                    <div className="mt-2 size-1.5 shrink-0 rounded-full bg-gray-400" />
                    <p className="text-sm leading-snug text-gray-600">{t('sec6.col3.rule3')}</p>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="mt-2 size-1.5 shrink-0 rounded-full bg-[#0068FF]" />
                    <p className="text-sm leading-snug font-medium text-gray-800">
                      {t('sec6.col3.rule4')}
                      <span className="ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-[#0068FF] align-middle" />
                    </p>
                  </div>

                  {/* Fading bottom edge */}
                  <div className="pointer-events-none absolute right-0 bottom-0 left-0 h-10 bg-gradient-to-t from-white to-transparent" />
                </div>

                <div className="flex gap-2 overflow-x-hidden px-4 pt-1 pb-4">
                  <button
                    type="button"
                    className="rounded-md bg-[#0068FF]/10 px-3 py-1.5 text-[11px] font-bold whitespace-nowrap text-[#0068FF] transition-colors hover:bg-[#0068FF]/20"
                  >
                    {t('sec6.col3.newRule')}
                  </button>
                  <button
                    type="button"
                    className="rounded-md bg-gray-100 px-3 py-1.5 text-[11px] font-bold whitespace-nowrap text-gray-600 transition-colors hover:bg-gray-200"
                  >
                    {t('sec6.col3.examples')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
