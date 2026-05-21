"use client";

import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import type { ComponentProps } from "react";

export type LoaderProps = ComponentProps<"div">;

export const Loader = ({ className, ...props }: LoaderProps) => (
  <div
    className={cn("flex items-center gap-2 text-muted-foreground", className)}
    role="status"
    {...props}
  >
    <Spinner className="size-4" />
  </div>
);
