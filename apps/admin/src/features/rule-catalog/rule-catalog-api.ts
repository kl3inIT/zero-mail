import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type RuleCatalogPersonasAdminResponse =
  components['schemas']['RuleCatalogPersonasAdminResponse'];
export type RuleCatalogActionsAdminResponse =
  components['schemas']['RuleCatalogActionsAdminResponse'];
export type RuleCatalogPersona = components['schemas']['RuleCatalogPersonaAdminResponse'];
export type RuleCatalogExample = components['schemas']['RuleCatalogExampleAdminResponse'];
export type RuleCatalogActionDescriptor =
  components['schemas']['RuleCatalogActionDescriptorAdminResponse'];
export type RuleCatalogPersonaWriteRequest =
  components['schemas']['RuleCatalogPersonaWriteRequest'];
export type RuleCatalogExampleWriteRequest =
  components['schemas']['RuleCatalogExampleWriteRequest'];
export type RuleCatalogActionDescriptorWriteRequest =
  components['schemas']['RuleCatalogActionDescriptorWriteRequest'];
export type RuleCatalogEnabledRequest = components['schemas']['RuleCatalogEnabledRequest'];
export type RuleCatalogReorderRequest = components['schemas']['RuleCatalogReorderRequest'];
export type RuleCatalogActionReorderRequest =
  components['schemas']['RuleCatalogActionReorderRequest'];
export type RuleCatalogMutationResponse =
  components['schemas']['RuleCatalogMutationResponse'];

export type SaveRuleCatalogPersonaInput = {
  personaId?: string;
  request: RuleCatalogPersonaWriteRequest;
};

export type SaveRuleCatalogExampleInput = {
  personaId?: string;
  exampleId?: string;
  request: RuleCatalogExampleWriteRequest;
};

export type SaveRuleCatalogActionDescriptorInput = {
  actionKey: string;
  request: RuleCatalogActionDescriptorWriteRequest;
};

export type SetRuleCatalogEnabledInput = {
  target: 'persona' | 'example' | 'action';
  targetId: string;
  enabled: boolean;
  reason: string;
};

export type ReorderRuleCatalogInput =
  | {
      target: 'personas';
      request: RuleCatalogReorderRequest;
    }
  | {
      target: 'examples';
      personaId: string;
      request: RuleCatalogReorderRequest;
    }
  | {
      target: 'actions';
      request: RuleCatalogActionReorderRequest;
    };

function errorFor(operation: string): Error {
  return new Error(`Không thể ${operation}.`);
}

export async function fetchRuleCatalogPersonas(): Promise<RuleCatalogPersonasAdminResponse> {
  const { data, error } = await api.GET('/api/admin/rule-catalog/personas');
  if (error || !data) {
    throw errorFor('tải danh mục ví dụ');
  }
  return data;
}

export async function fetchRuleCatalogActions(): Promise<RuleCatalogActionsAdminResponse> {
  const { data, error } = await api.GET('/api/admin/rule-catalog/actions');
  if (error || !data) {
    throw errorFor('tải danh mục hành động');
  }
  return data;
}

export async function saveRuleCatalogPersona(
  input: SaveRuleCatalogPersonaInput,
): Promise<RuleCatalogMutationResponse | void> {
  if (input.personaId) {
    const { error } = await api.PUT('/api/admin/rule-catalog/personas/{personaId}', {
      params: { path: { personaId: input.personaId } },
      body: input.request,
    });
    if (error) {
      throw errorFor('cập nhật persona');
    }
    return undefined;
  }

  const { data, error } = await api.POST('/api/admin/rule-catalog/personas', {
    body: input.request,
  });
  if (error || !data) {
    throw errorFor('tạo persona');
  }
  return data;
}

export async function saveRuleCatalogExample(
  input: SaveRuleCatalogExampleInput,
): Promise<RuleCatalogMutationResponse | void> {
  if (input.exampleId) {
    const { error } = await api.PUT('/api/admin/rule-catalog/examples/{exampleId}', {
      params: { path: { exampleId: input.exampleId } },
      body: input.request,
    });
    if (error) {
      throw errorFor('cập nhật ví dụ');
    }
    return undefined;
  }

  if (!input.personaId) {
    throw errorFor('tạo ví dụ vì thiếu persona');
  }
  const { data, error } = await api.POST(
    '/api/admin/rule-catalog/personas/{personaId}/examples',
    {
      params: { path: { personaId: input.personaId } },
      body: input.request,
    },
  );
  if (error || !data) {
    throw errorFor('tạo ví dụ');
  }
  return data;
}

export async function saveRuleCatalogActionDescriptor(
  input: SaveRuleCatalogActionDescriptorInput,
): Promise<void> {
  const { error } = await api.PUT('/api/admin/rule-catalog/actions/{actionKey}', {
    params: { path: { actionKey: input.actionKey } },
    body: input.request,
  });
  if (error) {
    throw errorFor('cập nhật hành động');
  }
}

export async function setRuleCatalogEnabled(input: SetRuleCatalogEnabledInput): Promise<void> {
  const request: RuleCatalogEnabledRequest = {
    enabled: input.enabled,
    reason: input.reason,
  };
  if (input.target === 'persona') {
    const { error } = await api.PATCH(
      '/api/admin/rule-catalog/personas/{personaId}/enabled',
      {
        params: { path: { personaId: input.targetId } },
        body: request,
      },
    );
    if (error) {
      throw errorFor('đổi trạng thái persona');
    }
    return;
  }

  if (input.target === 'example') {
    const { error } = await api.PATCH(
      '/api/admin/rule-catalog/examples/{exampleId}/enabled',
      {
        params: { path: { exampleId: input.targetId } },
        body: request,
      },
    );
    if (error) {
      throw errorFor('đổi trạng thái ví dụ');
    }
    return;
  }

  const { error } = await api.PATCH('/api/admin/rule-catalog/actions/{actionKey}/enabled', {
    params: { path: { actionKey: input.targetId } },
    body: request,
  });
  if (error) {
    throw errorFor('đổi trạng thái hành động');
  }
}

export async function reorderRuleCatalog(input: ReorderRuleCatalogInput): Promise<void> {
  if (input.target === 'personas') {
    const { error } = await api.PUT('/api/admin/rule-catalog/personas/reorder', {
      body: input.request,
    });
    if (error) {
      throw errorFor('sắp xếp persona');
    }
    return;
  }

  if (input.target === 'examples') {
    const { error } = await api.PUT('/api/admin/rule-catalog/examples/{personaId}/reorder', {
      params: { path: { personaId: input.personaId } },
      body: input.request,
    });
    if (error) {
      throw errorFor('sắp xếp ví dụ');
    }
    return;
  }

  const { error } = await api.PUT('/api/admin/rule-catalog/actions/reorder', {
    body: input.request,
  });
  if (error) {
    throw errorFor('sắp xếp hành động');
  }
}
