import { getTranslations } from 'next-intl/server';
import Image from 'next/image';

export default async function Testimonials() {
  const t = await getTranslations('landing.testimonials');

  const reviews = [
    {
      text: t('items.r1.text'),
      author: t('items.r1.author'),
      role: t('items.r1.role'),
      avatar: '/images/cus1.jpg',
    },
    {
      text: t('items.r2.text'),
      author: t('items.r2.author'),
      role: t('items.r2.role'),
      avatar: '/images/cus2.jpg',
    },
    {
      text: t('items.r3.text'),
      author: t('items.r3.author'),
      role: t('items.r3.role'),
      avatar: '/images/cus3.jpg',
    },
    {
      text: t('items.r4.text'),
      author: t('items.r4.author'),
      role: t('items.r4.role'),
      avatar: '/images/cus4.jpg',
    },
    {
      text: t('items.r5.text'),
      author: t('items.r5.author'),
      role: t('items.r5.role'),
      avatar: '/images/cus5.jpg',
    },
    {
      text: t('items.r6.text'),
      author: t('items.r6.author'),
      role: t('items.r6.role'),
      avatar: '/images/cus6.jpg',
    },
    {
      text: t('items.r7.text'),
      author: t('items.r7.author'),
      role: t('items.r7.role'),
      avatar: '/images/cus7.jpg',
    },
  ];

  return (
    <section className="zm-section bg-(--bg) py-24" id="testimonials">
      <div className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('title')}
          </h2>
          <p className="mx-auto max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            {t('subtitle')}
          </p>
        </div>

        <div className="mx-auto max-w-7xl columns-1 gap-6 space-y-6 md:columns-2 lg:columns-3">
          {reviews.map((review, i) => (
            <div
              key={i}
              className="break-inside-avoid rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-6 shadow-sm transition-shadow hover:shadow-md md:p-8"
            >
              <p className="mb-6 text-[15px] leading-relaxed text-(--text-muted) md:text-base">
                {review.text}
              </p>
              <div className="flex items-center gap-4">
                <div className="h-10 w-10 shrink-0 overflow-hidden rounded-full bg-gray-200">
                  <Image
                    src={review.avatar}
                    alt={review.author}
                    width={40}
                    height={40}
                    style={{ width: 'auto', height: 'auto' }}
                    className="object-cover"
                  />
                </div>
                <div>
                  <h4 className="text-[15px] font-bold text-(--ink)">{review.author}</h4>
                  <p className="text-[13px] text-(--text-faint)">{review.role}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
