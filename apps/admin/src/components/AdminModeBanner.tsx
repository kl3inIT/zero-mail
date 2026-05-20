type AdminModeBannerProps = {
  email: string;
  env: 'prod' | 'staging' | 'dev';
};

export function AdminModeBanner({ email, env }: AdminModeBannerProps) {
  return (
    <div className="sticky top-0 z-60 flex h-10 items-center border-b border-amber bg-warning-soft px-6 text-[12.5px] text-ink-2">
      <span className="font-mono text-[11px] font-semibold tracking-wider text-amber uppercase">
        CHẾ ĐỘ QUẢN TRỊ
      </span>
      <span className="mx-2 text-muted-foreground">•</span>
      <span>thao tác sẽ ảnh hưởng tới khách hàng thật</span>
      <span className="mx-2 text-muted-foreground">•</span>
      <span>
        đăng nhập với <span className="font-mono">{email}</span>
      </span>
      <span className="mx-2 text-muted-foreground">•</span>
      <span className="font-mono text-amber">{env}</span>
    </div>
  );
}
