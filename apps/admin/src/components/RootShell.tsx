import { Link, Outlet, type ErrorComponentProps } from '@tanstack/react-router';
import { AlertTriangleIcon, Loader2Icon, SearchXIcon } from 'lucide-react';

import { Button, buttonVariants } from '@/components/ui/button';
import { Toaster } from '@/components/ui/sonner';

export function RootShell() {
  return (
    <>
      <Outlet />
      <Toaster />
    </>
  );
}

export function RootErrorScreen({ error, reset }: ErrorComponentProps) {
  const message = error instanceof Error ? error.message : 'Lỗi không xác định';
  return (
    <div className="bg-background flex min-h-screen items-center justify-center p-6">
      <div className="bg-card w-full max-w-md rounded-xl border p-8 text-center shadow-sm">
        <div className="bg-destructive/10 text-destructive mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
          <AlertTriangleIcon className="size-6" />
        </div>
        <h1 className="text-foreground mb-2 text-lg font-semibold">Đã xảy ra lỗi</h1>
        <p className="text-muted-foreground mb-6 text-sm">{message}</p>
        <div className="flex justify-center gap-2">
          <Button variant="outline" onClick={() => reset()}>
            Thử lại
          </Button>
          <Link to="/" className={buttonVariants()}>
            Về bảng điều khiển
          </Link>
        </div>
      </div>
    </div>
  );
}

export function RootNotFoundScreen() {
  return (
    <div className="bg-background flex min-h-screen items-center justify-center p-6">
      <div className="bg-card w-full max-w-md rounded-xl border p-8 text-center shadow-sm">
        <div className="bg-muted text-muted-foreground mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
          <SearchXIcon className="size-6" />
        </div>
        <h1 className="text-foreground mb-2 text-lg font-semibold">Không tìm thấy trang</h1>
        <p className="text-muted-foreground mb-6 text-sm">
          Đường dẫn bạn truy cập không tồn tại hoặc đã bị di chuyển.
        </p>
        <Link to="/" className={buttonVariants()}>
          Về bảng điều khiển
        </Link>
      </div>
    </div>
  );
}

export function RootPendingScreen() {
  return (
    <div className="bg-background flex min-h-screen items-center justify-center">
      <Loader2Icon className="text-muted-foreground size-6 animate-spin" />
    </div>
  );
}
