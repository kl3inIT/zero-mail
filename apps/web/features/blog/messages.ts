/**
 * UI + SEO copy for the public blog (`/blog`, `/blog/[slug]`).
 * Merged into i18n/messages/{vi,en}.json by scripts/merge-feature-i18n.ts.
 */
export const blogMessages = {
  'blog.indexHeading': {
    vi: 'Blog',
    en: 'Blog',
  },
  'blog.indexTitle': {
    vi: 'Blog Zero Mail — Mẹo Gmail, năng suất & inbox zero',
    en: 'Zero Mail Blog — Gmail tips, productivity & inbox zero',
  },
  'blog.indexDescription': {
    vi: 'Hướng dẫn thực chiến về Gmail, tự động hoá email bằng AI và cách đạt inbox zero — viết bởi đội ngũ Zero Mail.',
    en: 'Practical guides on Gmail, AI email automation and reaching inbox zero — written by the Zero Mail team.',
  },
  'blog.empty': {
    vi: 'Chưa có bài viết nào. Hãy quay lại sau.',
    en: 'No posts yet. Check back soon.',
  },
  'blog.backToList': {
    vi: '← Quay lại Blog',
    en: '← Back to Blog',
  },
  'blog.publishedOn': {
    vi: 'Đăng ngày {date}',
    en: 'Published {date}',
  },
  'blog.updatedOn': {
    vi: 'Cập nhật {date}',
    en: 'Updated {date}',
  },
  'blog.by': {
    vi: 'bởi {author}',
    en: 'by {author}',
  },
  // Nav + footer entries that link the blog into the site (internal linking so
  // crawlers and users can discover /blog from every page).
  'nav.blog': {
    vi: 'Blog',
    en: 'Blog',
  },
  'footer.resources': {
    vi: 'Tài nguyên',
    en: 'Resources',
  },
  'footer.docs': {
    vi: 'Tài liệu',
    en: 'Docs',
  },
} as const;
