import Image from 'next/image';
import { getTranslations } from 'next-intl/server';

export async function FeatureChannels() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container text-center">
      <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
        {t('sec3.title')}
      </h2>
      <p className="mx-auto mb-20 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
        {t('sec3.body')}
      </p>

      <div className="flex w-full max-w-full items-center justify-center gap-4 overflow-visible py-12 sm:gap-16 md:gap-32">
        {/* Zalo */}
        <div className="group flex flex-col items-center gap-4 sm:gap-8">
          <div className="relative z-10 flex size-[85px] items-center justify-center rounded-full border border-(--line-strong) bg-(--bg-elevated) shadow-[0_4px_20px_rgba(0,0,0,0.05)] transition-all duration-500 hover:-translate-y-1 hover:shadow-[0_8px_30px_rgba(0,0,0,0.1)] sm:size-[110px]">
            {/* Ripple Rings */}
            <div className="absolute inset-0 -z-10 scale-[1.2] rounded-full border-[1.5px] border-(--line) opacity-50 transition-all duration-500 group-hover:scale-[1.25] group-hover:border-(--line-strong) group-hover:opacity-100" />
            <div className="absolute inset-0 -z-10 scale-[1.4] rounded-full border-[1.5px] border-(--line) opacity-20 transition-all duration-500 group-hover:scale-[1.5] group-hover:opacity-50" />

            <div className="relative h-16 w-16 overflow-hidden rounded-2xl sm:h-22 sm:w-22">
              <Image
                src="/images/zalo-icon.png"
                alt="Zalo"
                fill
                sizes="88px"
                className="object-cover"
              />
            </div>
          </div>
          <span className="text-base font-semibold text-(--text-muted) sm:text-lg">Zalo</span>
        </div>

        {/* Telegram */}
        <div className="group flex flex-col items-center gap-4 sm:gap-8">
          <div className="relative z-10 flex size-[85px] items-center justify-center rounded-full border border-(--line-strong) bg-(--bg-elevated) shadow-[0_4px_20px_rgba(0,0,0,0.05)] transition-all duration-500 hover:-translate-y-1 hover:shadow-[0_8px_30px_rgba(0,0,0,0.1)] sm:size-[110px]">
            {/* Ripple Rings */}
            <div className="absolute inset-0 -z-10 scale-[1.2] rounded-full border-[1.5px] border-(--line) opacity-50 transition-all duration-500 group-hover:scale-[1.25] group-hover:border-(--line-strong) group-hover:opacity-100" />
            <div className="absolute inset-0 -z-10 scale-[1.4] rounded-full border-[1.5px] border-(--line) opacity-20 transition-all duration-500 group-hover:scale-[1.5] group-hover:opacity-50" />

            <div className="relative h-14 w-14 overflow-hidden rounded-full sm:h-18 sm:w-18">
              <Image
                src="/images/telegram-icone-icon.png"
                alt="Telegram"
                fill
                sizes="72px"
                className="object-contain"
              />
            </div>
          </div>
          <span className="text-base font-semibold text-(--text-muted) sm:text-lg">Telegram</span>
        </div>

        {/* Web */}
        <div className="group flex flex-col items-center gap-4 sm:gap-8">
          <div className="relative z-10 flex size-[85px] items-center justify-center rounded-full border border-(--line-strong) bg-(--bg-elevated) shadow-[0_4px_20px_rgba(0,0,0,0.05)] transition-all duration-500 hover:-translate-y-1 hover:shadow-[0_8px_30px_rgba(0,0,0,0.1)] sm:size-[110px]">
            {/* Ripple Rings */}
            <div className="absolute inset-0 -z-10 scale-[1.2] rounded-full border-[1.5px] border-(--line) opacity-50 transition-all duration-500 group-hover:scale-[1.25] group-hover:border-(--line-strong) group-hover:opacity-100" />
            <div className="absolute inset-0 -z-10 scale-[1.4] rounded-full border-[1.5px] border-(--line) opacity-20 transition-all duration-500 group-hover:scale-[1.5] group-hover:opacity-50" />

            <div className="relative h-11 w-11 overflow-hidden rounded-full sm:h-[60px] sm:w-[60px]">
              <Image
                src="/images/website.jpeg"
                alt="Web"
                fill
                sizes="60px"
                className="object-contain"
              />
            </div>
          </div>
          <span className="text-base font-semibold text-(--text-muted) sm:text-lg">Web</span>
        </div>
      </div>
    </section>
  );
}
