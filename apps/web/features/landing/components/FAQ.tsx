export default function FAQ() {
  const faqs = [
    {
      q: 'Zero Mail hỗ trợ các nhà cung cấp email nào?',
      a: 'Chúng tôi hỗ trợ các tài khoản email Gmail, Google Workspace và Microsoft Outlook.',
    },
    {
      q: 'Làm thế nào tôi có thể yêu cầu tính năng mới?',
      a: 'Hãy gửi email cho chúng tôi. Chúng tôi luôn sẵn sàng lắng nghe để cải thiện trải nghiệm email của bạn.',
    },
    {
      q: 'Zero Mail có thay thế ứng dụng email hiện tại của tôi không?',
      a: 'Không! Zero Mail không phải là một ứng dụng email khách (email client). Nó được sử dụng song song với ứng dụng email hiện tại của bạn. Bạn vẫn sử dụng Google hoặc Outlook như bình thường.',
    },
    {
      q: 'Bạn có cung cấp hoàn tiền không?',
      a: 'Có, nếu bạn cảm thấy chúng tôi không mang lại giá trị cho bạn, hãy gửi email cho chúng tôi trong vòng 14 ngày kể từ khi nâng cấp và chúng tôi sẽ hoàn tiền cho bạn.',
    },
    {
      q: 'Tôi có thể dùng thử Zero Mail miễn phí không?',
      a: 'Chắc chắn rồi, chúng tôi có bản dùng thử miễn phí 7 ngày cho tất cả các gói để bạn có thể trải nghiệm ngay lập tức, không cần thẻ tín dụng!',
    },
  ];

  return (
    <>
      <section className="zm-section bg-(--bg) py-24" id="faq">
        <div className="zm-container max-w-5xl">
          <div className="mb-16 text-center">
            <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
              Các câu hỏi thường gặp
            </h2>
          </div>

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {faqs.map((faq, i) => (
              <div
                key={i}
                className="rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm"
              >
                <h4 className="mb-4 text-[17px] font-bold text-(--ink)">{faq.q}</h4>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">{faq.a}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
