import { Link, useMatches } from '@tanstack/react-router';

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';

type Crumb = {
  to: string;
  label: string;
};

const ROUTE_LABELS: Record<string, string> = {
  '/': 'Bảng điều khiển',
  '/audit': 'Nhật ký audit',
  '/role-grants': 'Phân quyền admin',
  '/master-keys': 'Quản lý LLM',
  '/tenants': 'Khách hàng',
  '/queue': 'Hàng đợi',
  '/spend': 'Chi phí',
};

function deriveLabel(routeId: string, params: Record<string, string>): string | null {
  const segments = routeId.replace(/^\/_authenticated/, '').split('/').filter(Boolean);
  if (segments.length === 0) return null;

  const lastSegment = segments[segments.length - 1];

  if (lastSegment === '$provider' && params.provider) return params.provider;
  if (lastSegment === '$tenantId' && params.tenantId) return params.tenantId;
  if (lastSegment === '$jobId' && params.jobId) return params.jobId;

  if (lastSegment.startsWith('$')) return null;

  const fullPath = '/' + segments.join('/');
  return ROUTE_LABELS[fullPath] ?? lastSegment;
}

export function AdminBreadcrumb() {
  const matches = useMatches();

  const rawCrumbs: Crumb[] = matches
    .filter((match) => match.routeId.startsWith('/_authenticated'))
    .map((match) => {
      const label = deriveLabel(
        match.routeId,
        match.params as Record<string, string>,
      );
      if (!label) return null;
      return { to: match.pathname, label } satisfies Crumb;
    })
    .filter((crumb): crumb is Crumb => crumb !== null);

  // Layout + index routes resolve to the same pathname/label — dedupe consecutive duplicates.
  const crumbs = rawCrumbs.filter(
    (crumb, index) => index === 0 || crumb.to !== rawCrumbs[index - 1].to,
  );

  if (crumbs.length === 0) return null;

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map((crumb, index) => {
          const isLast = index === crumbs.length - 1;
          return (
            <BreadcrumbItem key={crumb.to}>
              {isLast ? (
                <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
              ) : (
                <>
                  <BreadcrumbLink
                    render={(renderProps) => (
                      <Link to={crumb.to} {...renderProps}>
                        {crumb.label}
                      </Link>
                    )}
                  />
                  <BreadcrumbSeparator />
                </>
              )}
            </BreadcrumbItem>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
