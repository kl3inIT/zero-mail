import Image from 'next/image';

export default function Features() {
  return (
    <div className="flex flex-col gap-32 bg-(--bg) py-24" id="features">
      {/* 1. Automatically Organized */}
      <section className="zm-container text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          Tự động phân loại.
          <br />
          Không bao giờ bỏ lỡ email quan trọng.
        </h2>
        <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          Đang chìm ngập trong email? Đừng lãng phí năng lượng cố gắng ưu tiên chúng nữa. Trợ lý AI
          của chúng tôi sẽ tự động dán nhãn mọi thứ.
        </p>

        <div className="mx-auto mb-8 grid w-full max-w-5xl grid-cols-2">
          <div className="flex items-center justify-center gap-2 text-center">
            <span className="text-2xl font-bold text-(--text-muted)">Trước</span>
            <span className="relative -top-2 rounded-full bg-red-700 px-2 py-0.5 text-[11px] leading-none font-extrabold text-white shadow-sm">
              99+
            </span>
          </div>
          <div className="text-center">
            <span className="text-2xl font-bold text-(--text-muted)">Sau</span>
          </div>
        </div>

        <div className="relative mx-auto aspect-[16/9] w-full max-w-5xl overflow-hidden rounded-2xl">
          {/* Light Mode Image */}
          <Image
            src="/images/phan-loai-dark-v2.png"
            alt="Tính năng tự động phân loại"
            fill
            className="object-contain dark:hidden"
          />
          {/* Dark Mode Image */}
          <Image
            src="/images/phan-loai-light.png"
            alt="Tính năng tự động phân loại"
            fill
            className="hidden object-contain dark:block"
          />
        </div>
      </section>

      {/* 2. Pre-written drafts */}
      <section className="zm-container text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          Bản nháp chờ sẵn trong hộp thư
        </h2>
        <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          Khi bạn kiểm tra hộp thư, mọi email cần phản hồi sẽ có sẵn một bản nháp được soạn theo văn
          phong của bạn, sẵn sàng để gửi.
        </p>
        <div className="relative mx-auto aspect-[16/9] w-full max-w-5xl overflow-hidden rounded-2xl">
          {/* Light Mode Image */}
          <Image
            src="/images/ketnoi-light.png"
            alt="Bản nháp chờ sẵn"
            fill
            className="object-contain dark:hidden"
          />
          {/* Dark Mode Image */}
          <Image
            src="/images/ketnoi-dark.png"
            alt="Bản nháp chờ sẵn (Dark Mode)"
            fill
            className="hidden object-contain dark:block"
          />
        </div>
      </section>

      {/* 3. Your inbox, wherever you work */}
      <section className="zm-container text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          Hộp thư của bạn, ở bất cứ đâu
        </h2>
        <p className="mx-auto mb-20 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          Đọc email, soạn phản hồi và quản lý hộp thư từ Zalo, Telegram hoặc Web — không cần chuyển
          đổi ứng dụng.
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

      {/* 4. Get started in minutes */}
      <section className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Bắt đầu chỉ trong vài phút
          </h2>
          <p className="mx-auto max-w-3xl text-xl leading-relaxed text-(--text-muted)">
            Cài đặt chỉ với một click. Bắt đầu sắp xếp và soạn email trong vài phút.
          </p>
        </div>

        <div className="mx-auto max-w-6xl rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-4 shadow-sm md:p-6">
          <div className="grid grid-cols-1 gap-4 md:gap-6 lg:grid-cols-3">
            {/* Step 1 */}
            <div className="flex h-[420px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
              <div className="p-8 pb-0">
                <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-(--line-strong) bg-(--bg-elevated) px-3 py-1.5 text-[11px] font-bold tracking-wider text-(--text-muted)">
                  <svg
                    className="h-3.5 w-3.5 text-[#0068FF]"
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
                  STEP 1
                </div>
                <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                  Kết nối tài khoản Google
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Liên kết Gmail của bạn chỉ với hai cú nhấp chuột để bắt đầu trải nghiệm.
                </p>
              </div>
              <div className="relative flex flex-1 items-end justify-center overflow-hidden pb-12">
                <div className="flex gap-6">
                  <div className="relative z-10 flex size-24 items-center justify-center rounded-full border border-gray-100 bg-white shadow-[0_4px_20px_rgba(0,0,0,0.06)] transition-transform duration-500 hover:scale-105">
                    <div className="absolute inset-0 -z-10 scale-[1.3] rounded-full border border-gray-200 opacity-60" />
                    <div className="absolute inset-0 -z-10 scale-[1.6] rounded-full border border-gray-200 opacity-30" />
                    <svg className="h-12 w-12" viewBox="0 0 24 24" fill="none">
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
                    <svg className="h-12 w-12" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M11.636 1.708l-6.818 2.046v16.492l6.818 2.046V1.708z"
                        fill="#0078D4"
                      />
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
                    className="h-3.5 w-3.5 text-[#0068FF]"
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
                  STEP 2
                </div>
                <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                  Tổ chức hộp thư như ý muốn
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Danh mục thông minh tự động thiết lập. Dùng danh mục mặc định hoặc tự tạo.
                </p>
              </div>
              <div className="relative flex flex-1 items-end justify-center overflow-hidden pb-8">
                <div className="flex w-[140%] scale-[1.1] -rotate-2 flex-col gap-3.5 transition-transform duration-700 hover:scale-[1.15] hover:-rotate-1">
                  <div className="flex translate-x-4 justify-center gap-3">
                    <span className="rounded-md border border-purple-200 bg-purple-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-purple-700">
                      📧 Bản tin
                    </span>
                    <span className="rounded-md border border-blue-200 bg-blue-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-blue-700 shadow-sm shadow-blue-500/10">
                      ↩️ Cần trả lời
                    </span>
                    <span className="rounded-md border border-green-200 bg-green-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-green-700">
                      📢 Marketing
                    </span>
                    <span className="rounded-md border border-yellow-200 bg-yellow-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-yellow-700">
                      📅 Lịch hẹn
                    </span>
                  </div>
                  <div className="flex -translate-x-6 justify-center gap-3">
                    <span className="rounded-md border border-red-200 bg-red-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-red-700">
                      🔔 Thông báo
                    </span>
                    <span className="rounded-md border border-cyan-200 bg-cyan-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-cyan-700 shadow-sm shadow-cyan-500/10">
                      ❄️ Cold Email
                    </span>
                    <span className="rounded-md border border-orange-200 bg-orange-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-orange-700">
                      👥 Nội bộ
                    </span>
                    <span className="rounded-md border border-pink-200 bg-pink-100/50 px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-pink-700">
                      🔥 Khẩn cấp
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
                    className="h-3.5 w-3.5 text-[#0068FF]"
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
                  STEP 3
                </div>
                <h3 className="mb-3 text-2xl leading-tight font-bold text-(--ink)">
                  Bản nháp tự động thông minh
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Mọi email cần phản hồi sẽ luôn có một bản nháp chờ bạn bấm Gửi.
                </p>
              </div>
              <div className="relative flex flex-1 items-end justify-center pt-8">
                <div className="w-[88%] translate-y-3 self-end overflow-hidden rounded-t-xl border border-gray-200 bg-white shadow-[0_-10px_30px_rgba(0,0,0,0.05)] transition-transform duration-500 hover:translate-y-1">
                  <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50 px-5 py-3">
                    <span className="text-[12px] font-semibold text-gray-800">New Message</span>
                    <svg
                      className="h-3 w-3 text-gray-400"
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
                    <span className="bg-gradient-to-r from-blue-600 to-cyan-500 bg-clip-text text-[10px] font-black tracking-widest text-transparent">
                      DRAFTED BY AI:
                    </span>
                    <p className="text-[18px] leading-snug font-medium text-gray-700">
                      Tôi rất sẵn lòng gặp bạn lúc 5h chiều tuần tới nhé!
                    </p>
                    <div className="mt-4 flex items-center">
                      <button className="group relative cursor-pointer overflow-hidden rounded-full bg-[#0b57d0] px-6 py-2 text-sm font-semibold text-white shadow-md transition-all hover:bg-blue-700 hover:shadow-lg">
                        <span className="relative z-10 flex items-center gap-1.5">Send</span>
                        <div className="absolute inset-0 translate-y-full bg-white/20 transition-transform duration-300 group-hover:translate-y-0" />
                      </button>
                      <svg
                        className="absolute left-20 z-20 -mt-2 ml-2 h-5 w-5 animate-bounce text-gray-800"
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

      {/* 5. Bulk unsubscribe */}
      <section className="zm-container mb-24 text-center">
        <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
          Hủy đăng ký hàng loạt email bạn không bao giờ đọc
        </h2>
        <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
          Xem những email nào bạn chưa bao giờ mở, và hủy đăng ký & lưu trữ chúng chỉ với một cú
          nhấp chuột.
        </p>
        <div className="relative mx-auto aspect-[16/9] w-full max-w-4xl overflow-hidden rounded-2xl border border-(--line-strong) bg-(--bg-elevated) shadow-[0_8px_30px_rgba(0,0,0,0.06)] md:aspect-[2/1]">
          <Image
            src="/images/huydangky-light.png"
            alt="Hủy đăng ký hàng loạt"
            fill
            className="object-contain p-4 md:p-8 dark:hidden"
          />
          <Image
            src="/images/huydangky-dark.png"
            alt="Hủy đăng ký hàng loạt"
            fill
            className="hidden object-contain p-4 md:p-8 dark:block"
          />
        </div>
      </section>

      {/* 6. Designed around you */}
      <section className="zm-container mb-24">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Thiết kế xoay quanh cách bạn làm việc
          </h2>
          <p className="mx-auto max-w-3xl text-xl leading-relaxed text-(--text-muted)">
            Đủ linh hoạt cho mọi quy trình. Đủ đơn giản để cài đặt trong vài phút.
          </p>
        </div>

        <div className="mx-auto max-w-7xl rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-4 shadow-sm md:p-6">
          <div className="grid grid-cols-1 gap-4 md:gap-6 lg:grid-cols-3">
            {/* Col 1 */}
            <div className="flex min-h-[460px] flex-col overflow-hidden rounded-[24px] border border-(--line) bg-(--bg) shadow-sm transition-shadow hover:shadow-md">
              <div className="p-8 pb-0">
                <div className="mb-5 flex h-8 w-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M4 20h3V10H4v10zm6 0h3V4h-3v16zm6 0h3v-8h-3v8z" />
                  </svg>
                </div>
                <h3 className="mb-3 pr-4 text-2xl leading-tight font-bold text-(--ink)">
                  Phân tích hộp thư. Hiểu rõ để tối ưu
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Xem ai gửi email nhiều nhất và điều gì đang làm đầy hộp thư. Phân tích chi tiết và
                  xử lý.
                </p>
              </div>
              <div className="relative flex flex-1 items-end justify-center px-6 pt-8 pb-0">
                <div className="relative w-full translate-y-3 overflow-hidden rounded-t-[20px] border border-b-0 border-gray-200 bg-white shadow-[0_-4px_20px_rgba(0,0,0,0.04)] transition-transform duration-500 hover:translate-y-1">
                  <div className="p-5">
                    <h4 className="mb-4 text-[15px] font-semibold text-gray-600">
                      Ai gửi email cho bạn nhiều nhất
                    </h4>
                    <div className="mb-2 flex justify-between text-[11px] font-semibold tracking-wider text-gray-600 uppercase">
                      <span>Người gửi</span>
                      <span>Số lượng</span>
                    </div>
                    <div className="flex flex-col gap-2">
                      <div className="relative z-10 flex items-center justify-between text-sm">
                        <div className="absolute inset-0 -z-10 w-[85%] rounded-md bg-[#0068FF]/15" />
                        <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                          Stripe{' '}
                          <span className="font-normal text-gray-400">
                            &lt;notifications...&gt;
                          </span>
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
                          beehiiv{' '}
                          <span className="font-normal text-gray-400">&lt;buzz@...&gt;</span>
                        </span>
                        <span className="px-2 font-semibold text-gray-600">32</span>
                      </div>
                      <div className="relative z-10 flex items-center justify-between text-sm opacity-50">
                        <div className="absolute inset-0 -z-10 w-[30%] rounded-md bg-[#0068FF]/10" />
                        <span className="truncate px-2 py-1.5 font-semibold text-gray-800">
                          Mailgun{' '}
                          <span className="font-normal text-gray-400">&lt;report...&gt;</span>
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
                <div className="mb-5 flex h-8 w-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                  <svg
                    className="h-4 w-4"
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
                  Bản nháp nắm rõ lịch trình của bạn
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Kết nối với lịch và CRM của bạn để soạn email dựa trên thời gian biểu và dữ liệu
                  khách hàng.
                </p>
              </div>
              <div className="relative flex flex-1 items-center justify-center overflow-hidden p-8">
                <div className="grid grid-cols-3 gap-5">
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M11.636 1.708l-6.818 2.046v16.492l6.818 2.046V1.708z"
                        fill="#0078D4"
                      />
                      <path d="M4.818 3.754H1.364v16.492h3.454V3.754z" fill="#50E6FF" />
                      <path d="M11.636 1.708h11V22.29h-11V1.708z" fill="#28A8EA" />
                      <path
                        d="M16 16.602l-1.282-1.364h1.75c.99 0 1.56-.516 1.56-1.362V9.824c0-.847-.57-1.364-1.56-1.364h-1.75l1.282-1.364h1.96c1.613 0 2.802.99 2.802 2.656v4.204c0 1.666-1.189 2.646-2.802 2.646H16z"
                        fill="#fff"
                      />
                    </svg>
                  </div>
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M17 2H7C4.23858 2 2 4.23858 2 7V17C2 19.7614 4.23858 22 7 22H17C19.7614 22 22 19.7614 22 17V7C22 4.23858 19.7614 2 17 2Z"
                        fill="white"
                      />
                      <path
                        d="M17 2H7C4.23858 2 2 4.23858 2 7V17C2 19.7614 4.23858 22 7 22H17C19.7614 22 22 19.7614 22 17V7C22 4.23858 19.7614 2 17 2Z"
                        fill="#F4F4F4"
                      />
                      <path
                        d="M16.5 22H7.5C4.46243 22 2 19.5376 2 16.5V7.5C2 4.46243 4.46243 2 7.5 2H16.5C19.5376 2 22 4.46243 22 7.5V16.5C22 19.5376 19.5376 22 16.5 22Z"
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
                        d="M12 11.5H13.5V13.5C13.5 14.6046 12.6046 15.5 11.5 15.5H10"
                        stroke="#34A853"
                        strokeWidth="1.2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                      <path
                        d="M10 11.5H11.5V9.5C11.5 8.39543 12.3954 7.5 13.5 7.5H15"
                        stroke="#EA4335"
                        strokeWidth="1.2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </div>
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="black">
                      <path d="M4 4h4l8 11V4h4v16h-4L4 9v11H0V4h4z" />
                    </svg>
                  </div>
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="none">
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
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
                    <span className="text-[17px] font-extrabold tracking-tighter text-[#635BFF]">
                      stripe
                    </span>
                  </div>
                  <div className="flex h-14 w-14 cursor-pointer items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-[0_4px_15px_rgba(0,0,0,0.06)] transition-transform hover:scale-110">
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
                <div className="mb-5 flex h-8 w-8 items-center justify-center rounded-lg border border-(--line-strong) bg-(--bg-elevated) text-(--ink) shadow-sm">
                  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z" />
                  </svg>
                </div>
                <h3 className="mb-3 pr-4 text-2xl leading-tight font-bold text-(--ink)">
                  Tùy biến bằng ngôn ngữ tự nhiên
                </h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  Hộp thư của bạn, luật của bạn. Cấu hình mọi thứ bằng tiếng Việt tự nhiên.
                </p>
              </div>
              <div className="relative flex flex-1 items-end justify-center px-6 pt-8 pb-6">
                <div className="flex w-full translate-y-2 flex-col overflow-hidden rounded-[16px] border border-gray-200 bg-white shadow-[0_4px_20px_rgba(0,0,0,0.04)] transition-transform duration-500 hover:translate-y-0">
                  <div className="relative flex flex-1 flex-col gap-3 p-5">
                    <div className="flex items-start gap-3 opacity-90">
                      <div className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400" />
                      <p className="text-sm leading-snug text-gray-600">
                        Gắn nhãn email từ @congty.com là Nội bộ
                      </p>
                    </div>
                    <div className="flex items-start gap-3 opacity-80">
                      <div className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400" />
                      <p className="text-sm leading-snug text-gray-600">
                        Lưu trữ mọi hóa đơn và gửi cho kế toán
                      </p>
                    </div>
                    <div className="flex items-start gap-3 opacity-60">
                      <div className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-gray-400" />
                      <p className="text-sm leading-snug text-gray-600">
                        Gửi bản tổng hợp newsletter vào CN
                      </p>
                    </div>
                    <div className="flex items-start gap-3">
                      <div className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-[#0068FF]" />
                      <p className="text-sm leading-snug font-medium text-gray-800">
                        Nếu có ai muốn đặt lịch họp, hãy phản hồi k
                        <span className="ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-[#0068FF] align-middle" />
                      </p>
                    </div>

                    {/* Fading bottom edge */}
                    <div className="pointer-events-none absolute right-0 bottom-0 left-0 h-10 bg-gradient-to-t from-white to-transparent" />
                  </div>

                  <div className="flex gap-2 overflow-x-hidden px-4 pt-1 pb-4">
                    <button className="rounded-md bg-[#0068FF]/10 px-3 py-1.5 text-[11px] font-bold whitespace-nowrap text-[#0068FF] transition-colors hover:bg-[#0068FF]/20">
                      + Tạo luật mới
                    </button>
                    <button className="rounded-md bg-gray-100 px-3 py-1.5 text-[11px] font-bold whitespace-nowrap text-gray-600 transition-colors hover:bg-gray-200">
                      ↗ Xem ví dụ
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
