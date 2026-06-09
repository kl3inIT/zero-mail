import Image from 'next/image';
import { getTranslations } from 'next-intl/server';

const REVIEW_META = [
  { id: 'r1', author: 'Trần Việt Anh', avatar: '/images/cus1.jpg' },
  { id: 'r2', author: 'Nguyễn Minh Tuấn', avatar: '/images/cus2.jpg' },
  { id: 'r3', author: 'Phạm Minh Hoàng', avatar: '/images/cus3.jpg' },
  { id: 'r4', author: 'Lê Hoàng Long', avatar: '/images/cus4.jpg' },
  { id: 'r5', author: 'Nguyễn Đức Huy', avatar: '/images/cus5.jpg' },
  { id: 'r6', author: 'Nguyễn Thị Mai Hương', avatar: '/images/cus6.jpg' },
  { id: 'r7', author: 'Đặng Tiến Minh', avatar: '/images/cus7.jpg' },
] as const;

export default async function Testimonials() {
  const t = await getTranslations('landingTestimonials');

  const reviews = REVIEW_META.map((meta) => ({
    id: meta.id,
    text: t(`${meta.id}.text`),
    author: meta.author,
    role: t(`${meta.id}.role`),
    avatar: meta.avatar,
  }));

  return (
    <section className="zm-section bg-(--bg) py-24" id="testimonials">
      <div className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('header.title')}
          </h2>
          <p className="mx-auto max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            {t('header.body')}
          </p>
        </div>

        <div className="mx-auto max-w-7xl columns-1 gap-6 space-y-6 md:columns-2 lg:columns-3">
          {reviews.map((review) => (
            <div
              key={review.id}
              className="break-inside-avoid rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-6 shadow-sm transition-shadow hover:shadow-md md:p-8"
            >
              <p className="mb-6 text-[15px] leading-relaxed text-(--text-muted) md:text-base">
                {review.text}
              </p>
              <div className="flex items-center gap-4">
                <div className="size-10 shrink-0 overflow-hidden rounded-full bg-gray-200">
                  <Image
                    src={review.avatar}
                    alt={review.author}
                    width={40}
                    height={40}
                    className="object-cover"
                  />
                </div>
                <div>
                  <p className="text-[15px] font-bold text-(--ink)">{review.author}</p>
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
