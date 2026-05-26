import {api} from '@/lib/api/admin-client';
import type {components} from '@/lib/api/admin-schema';

export type BillingPackageAdminResponse =
    components['schemas']['BillingPackageAdminResponse'];
export type BillingPackageAdminListResponse =
    components['schemas']['BillingPackageAdminListResponse'];
export type BillingPackageAdminCreateRequest =
    components['schemas']['BillingPackageAdminCreateRequest'];
export type BillingPackageAdminUpdateRequest =
    components['schemas']['BillingPackageAdminUpdateRequest'];
export type BillingPackageReorderRequest =
    components['schemas']['BillingPackageReorderRequest'];

function errorFor(operation: string): Error {
    return new Error(`Không thể ${operation}.`);
}

export async function fetchBillingPackages(): Promise<BillingPackageAdminListResponse> {
    const {data, error} = await api.GET('/api/admin/billing/packages');
    if (error || !data) {
        throw errorFor('tải danh sách gói');
    }
    return data;
}

export async function createBillingPackage(
    request: BillingPackageAdminCreateRequest,
): Promise<BillingPackageAdminResponse> {
    const {data, error} = await api.POST('/api/admin/billing/packages', {
        body: request,
    });
    if (error || !data) {
        throw errorFor('tạo gói');
    }
    return data;
}

export async function updateBillingPackage(input: {
    packageId: string;
    request: BillingPackageAdminUpdateRequest;
}): Promise<BillingPackageAdminResponse> {
    const {data, error} = await api.PATCH('/api/admin/billing/packages/{packageId}', {
        params: {path: {packageId: input.packageId}},
        body: input.request,
    });
    if (error || !data) {
        throw errorFor('cập nhật gói');
    }
    return data;
}

export async function activateBillingPackage(
    packageId: string,
): Promise<BillingPackageAdminResponse> {
    const {data, error} = await api.POST(
        '/api/admin/billing/packages/{packageId}/activate',
        {
            params: {path: {packageId}},
        },
    );
    if (error || !data) {
        throw errorFor('bật gói');
    }
    return data;
}

export async function deactivateBillingPackage(
    packageId: string,
): Promise<BillingPackageAdminResponse> {
    const {data, error} = await api.POST(
        '/api/admin/billing/packages/{packageId}/deactivate',
        {
            params: {path: {packageId}},
        },
    );
    if (error || !data) {
        throw errorFor('tắt gói');
    }
    return data;
}

export async function reorderBillingPackages(
    request: BillingPackageReorderRequest,
): Promise<BillingPackageAdminListResponse> {
    const {data, error} = await api.POST('/api/admin/billing/packages/reorder', {
        body: request,
    });
    if (error || !data) {
        throw errorFor('đổi thứ tự gói');
    }
    return data;
}
