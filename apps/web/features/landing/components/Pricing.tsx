export default function Pricing() {
  return (
    <section className="zm-section bg-(--bg)" id="pricing">
      <div className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Dùng thử miễn phí, gói trả phí hợp lý
          </h2>
          <p className="mx-auto mb-10 max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            Không có phí ẩn. Hủy bất cứ lúc nào.
          </p>

          <p className="inline-flex items-center rounded-full border border-(--line-strong) bg-(--bg-elevated) px-5 py-2 text-sm font-semibold text-(--text-muted) shadow-sm">
            Giá theo năm, dùng thử miễn phí 7 ngày
          </p>
        </div>

        <div className="mx-auto grid max-w-6xl grid-cols-1 gap-6 md:grid-cols-3">
          {/* Starter */}
          <div className="flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="mb-6 flex items-start justify-between">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                💼
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">Starter</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              Dành cho cá nhân, doanh nhân muốn lấy lại thời gian của mình.
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$18</span>
              <span className="text-sm text-(--text-muted)"> /người /tháng</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl border border-(--line-strong) text-base font-semibold text-(--ink) hover:bg-(--bg-subtle)"
            >
              Dùng thử miễn phí 7 ngày
            </a>
            <div className="flex-1">
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Sắp xếp và gắn nhãn mọi email
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Soạn nháp theo văn phong của bạn
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Chặn email quảng cáo, rác
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Hủy đăng ký và lưu trữ hàng loạt
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Phân tích dữ liệu email
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Tóm tắt trước cuộc họp
                </li>
              </ul>
            </div>
          </div>

          {/* Plus */}
          <div className="relative flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="pointer-events-none absolute inset-0 rounded-[32px] border-2 border-blue-500" />
            <div className="mb-6 flex items-start justify-between">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                ⚡
              </div>
              <div className="flex gap-2">
                <span className="inline-flex items-center rounded-full bg-green-100 px-3 py-1 text-xs font-bold text-green-700">
                  Phổ biến
                </span>
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">Plus</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              Dành cho người dùng chuyên sâu cần các tiện ích tích hợp hệ thống.
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$28</span>
              <span className="text-sm text-(--text-muted)"> /người /tháng</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl bg-[#3367D6] text-base font-semibold text-white shadow-md transition-all hover:bg-[#2851A8] hover:shadow-lg"
            >
              Dùng thử miễn phí 7 ngày
            </a>
            <div className="flex-1">
              <p className="mb-4 text-sm font-semibold text-(--ink)">
                Mọi thứ trong gói Starter, cộng thêm:
              </p>
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  2 tài khoản email /người dùng
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Tích hợp Slack / Discord
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Gửi bản tin tổng hợp hàng tuần
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Tự động lưu trữ tệp đính kèm
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Kho lưu trữ kiến thức không giới hạn
                </li>
              </ul>
            </div>
          </div>

          {/* Professional */}
          <div className="flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="mb-6 flex items-start justify-between">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                ✨
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">Professional</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              Dành cho đội ngũ và doanh nghiệp xử lý lượng lớn email.
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$42</span>
              <span className="text-sm text-(--text-muted)"> /người /tháng</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl border border-(--line-strong) text-base font-semibold text-(--ink) hover:bg-(--bg-subtle)"
            >
              Dùng thử miễn phí 7 ngày
            </a>
            <div className="flex-1">
              <p className="mb-4 text-sm font-semibold text-(--ink)">
                Mọi thứ trong gói Plus, cộng thêm:
              </p>
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Bảng phân tích toàn nhóm
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Hỗ trợ khách hàng ưu tiên 24/7
                </li>
                <li className="flex items-start gap-3">
                  <svg
                    className="mt-0.5 h-5 w-5 shrink-0 text-blue-500"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                  Chuyên viên hỗ trợ cài đặt riêng
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
