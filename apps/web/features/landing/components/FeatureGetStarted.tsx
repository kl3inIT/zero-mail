import { getTranslations } from 'next-intl/server';

export async function FeatureGetStarted() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container">
      <div className="mb-16 text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          {t('sec4.title')}
        </h2>
        <p className="mx-auto max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          {t('sec4.body')}
        </p>
      </div>

      <div className="mx-auto max-w-6xl rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-4 shadow-sm md:p-6">
        <div className="grid grid-cols-1 gap-4 md:gap-6 lg:grid-cols-3">
          {/* Step 1 */}
          <div className="flex h-[420px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-(--line-strong) bg-(--bg-elevated) px-3 py-1.5 text-[11px] font-bold tracking-wider text-(--text-muted)">
                <svg
                  className="size-3.5 text-[#0068FF]"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                {t('sec4.step1.label')}
              </div>
              <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec4.step1.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec4.step1.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-end justify-center overflow-hidden pb-12">
              <div className="flex gap-6">
                <div className="relative z-10 flex size-24 items-center justify-center rounded-full border border-gray-100 bg-white shadow-[0_4px_20px_rgba(0,0,0,0.06)] transition-transform duration-500 hover:scale-105">
                  <div className="absolute inset-0 -z-10 scale-[1.3] rounded-full border border-gray-200 opacity-60" />
                  <div className="absolute inset-0 -z-10 scale-[1.6] rounded-full border border-gray-200 opacity-30" />
                  <svg className="size-12" viewBox="0 0 24 24" fill="none">
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
                <div className="relative z-10 flex size-24 items-center justify-center rounded-full border border-gray-100 bg-white shadow-[0_4px_20px_rgba(0,0,0,0.06)] transition-transform duration-500 hover:scale-105">
                  <div className="absolute inset-0 -z-10 scale-[1.3] rounded-full border border-gray-200 opacity-60" />
                  <div className="absolute inset-0 -z-10 scale-[1.6] rounded-full border border-gray-200 opacity-30" />
                  <svg className="size-12" viewBox="0 0 24 24" fill="none">
                    <path d="M11.636 1.708l-6.818 2.046v16.492l6.818 2.046V1.708z" fill="#0078D4" />
                    <path d="M4.818 3.754H1.364v16.492h3.454V3.754z" fill="#50E6FF" />
                    <path d="M11.636 1.708h11V22.29h-11V1.708z" fill="#28A8EA" />
                    <path
                      d="M16 16.602l-1.282-1.364h1.75c.99 0 1.56-.516 1.56-1.362V9.824c0-.847-.57-1.364-1.56-1.364h-1.75l1.282-1.364h1.96c1.613 0 2.802.99 2.802 2.656v4.204c0 1.666-1.189 2.646-2.802 2.646H16z"
                      fill="#fff"
                    />
                  </svg>
                </div>
              </div>
            </div>
          </div>

          {/* Step 2 */}
          <div className="flex h-[420px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-(--line-strong) bg-(--bg-elevated) px-3 py-1.5 text-[11px] font-bold tracking-wider text-(--text-muted)">
                <svg
                  className="size-3.5 text-[#0068FF]"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M3 4h13M3 8h9m-9 4h6m4 0l4-4m0 0l4 4m-4-4v12"
                  />
                </svg>
                {t('sec4.step2.label')}
              </div>
              <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec4.step2.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec4.step2.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-end justify-center overflow-hidden pb-8">
              <div className="flex w-[140%] scale-[1.1] -rotate-2 flex-col gap-3.5 transition-transform duration-700 hover:scale-[1.15] hover:-rotate-1">
                <div className="flex translate-x-4 justify-center gap-3">
                  <span className="rounded-md border border-purple-200 bg-purple-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-purple-700">
                    {t('sec4.step2.chips.newsletter')}
                  </span>
                  <span className="rounded-md border border-blue-200 bg-blue-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-blue-700 shadow-sm shadow-blue-500/10">
                    {t('sec4.step2.chips.needsReply')}
                  </span>
                  <span className="rounded-md border border-green-200 bg-green-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-green-700">
                    {t('sec4.step2.chips.marketing')}
                  </span>
                  <span className="rounded-md border border-yellow-200 bg-yellow-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-yellow-700">
                    {t('sec4.step2.chips.calendar')}
                  </span>
                </div>
                <div className="flex -translate-x-6 justify-center gap-3">
                  <span className="rounded-md border border-red-200 bg-red-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-red-700">
                    {t('sec4.step2.chips.notifications')}
                  </span>
                  <span className="rounded-md border border-cyan-200 bg-cyan-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-cyan-700 shadow-sm shadow-cyan-500/10">
                    {t('sec4.step2.chips.coldEmail')}
                  </span>
                  <span className="rounded-md border border-orange-200 bg-orange-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-orange-700">
                    {t('sec4.step2.chips.internal')}
                  </span>
                  <span className="rounded-md border border-pink-200 bg-pink-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-pink-700">
                    {t('sec4.step2.chips.urgent')}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Step 3 */}
          <div className="flex h-[420px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
            <div className="p-8 pb-0">
              <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-(--line-strong) bg-(--bg-elevated) px-3 py-1.5 text-[11px] font-bold tracking-wider text-(--text-muted)">
                <svg
                  className="size-3.5 text-[#0068FF]"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                {t('sec4.step3.label')}
              </div>
              <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                {t('sec4.step3.title')}
              </h3>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">
                {t('sec4.step3.body')}
              </p>
            </div>
            <div className="relative flex flex-1 items-end justify-center pt-8">
              <div className="w-[88%] translate-y-3 self-end overflow-hidden rounded-t-xl border border-gray-200 bg-white shadow-[0_-10px_30px_rgba(0,0,0,0.05)] transition-transform duration-500 hover:translate-y-1">
                <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50 px-5 py-3">
                  <span className="text-[12px] font-semibold text-gray-800">
                    {t('sec4.step3.mockHeader')}
                  </span>
                  <svg
                    className="size-3 text-gray-400"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M6 18L18 6M6 6l12 12"
                    />
                  </svg>
                </div>
                <div className="flex flex-col gap-3 p-5">
                  <span className="text-[10px] font-black tracking-widest text-blue-600">
                    {t('sec4.step3.draftedBy')}
                  </span>
                  <p className="text-[18px] leading-snug font-medium text-gray-700">
                    {t('sec4.step3.draftBody')}
                  </p>
                  <div className="mt-4 flex items-center">
                    <button
                      type="button"
                      className="group relative cursor-pointer overflow-hidden rounded-full bg-[#0b57d0] px-6 py-2 text-sm font-semibold text-white shadow-md transition-all hover:bg-blue-700 hover:shadow-lg"
                    >
                      <span className="relative z-10 flex items-center gap-1.5">
                        {t('sec4.step3.sendButton')}
                      </span>
                      <div className="absolute inset-0 translate-y-full bg-white/20 transition-transform duration-300 group-hover:translate-y-0" />
                    </button>
                    <svg
                      className="absolute left-20 z-20 -mt-2 ml-2 size-5 animate-pulse text-gray-800"
                      viewBox="0 0 24 24"
                      fill="currentColor"
                    >
                      <path
                        d="M13.5 21l-3-6-6-3L22 2l-8.5 19z"
                        stroke="white"
                        strokeWidth="2"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
