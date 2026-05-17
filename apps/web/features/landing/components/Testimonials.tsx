import Image from 'next/image';

export default function Testimonials() {
  const reviews = [
    {
      text: 'Yêu thích ứng dụng tự động hóa email tuyệt vời này: zeromail.com',
      author: 'Trần Việt Anh',
      role: 'CTO',
      avatar: '/images/cus1.jpg',
    },
    {
      text: 'Thật sự tuyệt vời! Đã dọn dẹp các email quảng cáo và newsletter rác chỉ trong vài phút.',
      author: 'Nguyễn Minh Tuấn',
      role: 'Giám đốc Kỹ thuật',
      avatar: '/images/cus2.jpg',
    },
    {
      text: 'Wow. Đã thiết lập xong và hủy đăng ký khỏi những kẻ gửi spam tồi tệ nhất chỉ trong 3 phút... Cảm ơn 🙏',
      author: 'Phạm Minh Hoàng',
      role: 'Trưởng phòng Growth',
      avatar: '/images/cus3.jpg',
    },
    {
      text: 'Đây là công cụ đầu tiên tôi thử mà thực sự nắm bắt được văn phong của tôi trong các bản nháp phản hồi.',
      author: 'Lê Hoàng Long',
      role: 'Kỹ sư Pháp chế',
      avatar: '/images/cus4.jpg',
    },
    {
      text: 'Tôi tìm thấy Zero Mail khi đang tìm một trợ lý ảo để quản lý email, nhưng sau khi dùng thử công cụ này, mọi thứ đã thay đổi hoàn toàn.',
      author: 'Nguyễn Đức Huy',
      role: 'Nhà sáng lập',
      avatar: '/images/cus5.jpg',
    },
    {
      text: 'Tôi là một giám đốc điều hành đang chìm ngập trong hàng trăm email mỗi ngày. Điều tôi yêu thích nhất ở Zero Mail là cách nó thay thế hoàn toàn việc xử lý email thủ công—tự động hóa thông minh.',
      author: 'Nguyễn Thị Mai Hương',
      role: 'Giám đốc Điều hành',
      avatar: '/images/cus6.jpg',
    },
    {
      text: "Cuối cùng cũng có một ứng dụng 'hủy đăng ký' giúp bạn *thực sự* hủy đăng ký và lọc thông qua bộ lọc Gmail (thay vì luôn phụ thuộc vào ứng dụng bên thứ 3).",
      author: 'Đặng Tiến Minh',
      role: 'Nhà sáng lập',
      avatar: '/images/cus7.jpg',
    },
  ];

  return (
    <section className="zm-section bg-(--bg) py-24" id="testimonials">
      <div className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Gia nhập cùng 20,000+ người đang tiết kiệm thời gian email
          </h2>
          <p className="mx-auto max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            Khách hàng của chúng tôi yêu thích việc tiết kiệm thời gian với Zero Mail.
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
