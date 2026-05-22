import { Outlet, createFileRoute } from '@tanstack/react-router';

/**
 * Pathless layout for the master-keys section. Both the list (index) and
 * the per-provider detail render as children — so the detail page is the
 * full surface, not a stacked sub-section of the list.
 */
export const Route = createFileRoute('/_authenticated/master-keys')({
  component: MasterKeysLayout,
});

function MasterKeysLayout() {
  return <Outlet />;
}
