import type {components} from './admin-schema';

export type ApiErrorPayload = components['schemas']['ApiError'];

const FALLBACK_MESSAGE = 'Có lỗi xảy ra. Vui lòng thử lại.';

const ERROR_MESSAGES_VI: Record<string, string> = {
    'auth.unauthorized': 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    'auth.forbidden': 'Bạn không có quyền thực hiện hành động này.',
    'auth.passkey.invalid': 'Passkey không hợp lệ.',
    'validation': 'Dữ liệu nhập không hợp lệ.',
    'validation.generic': 'Dữ liệu nhập không hợp lệ.',
    'catalog.model.duplicate': 'Mô hình đã tồn tại trong danh mục.',
    'catalog.sync.in_progress': 'Đang có phiên đồng bộ khác chạy.',
    'admin.billing.package_not_found': 'Không tìm thấy gói thanh toán.',
    'admin.billing.package_code_duplicate': 'Code gói thanh toán đã tồn tại.',
    'admin.billing.package_invalid': 'Dữ liệu gói thanh toán không hợp lệ.',
    'master_key.invalid': 'Khóa nền tảng không hợp lệ.',
    'tenant.not_found': 'Không tìm thấy khách hàng.',
    'rate_limit.exceeded': 'Bạn thao tác quá nhanh. Vui lòng chờ và thử lại.',
    'unknown': FALLBACK_MESSAGE,
};

function stripPrefix(code: string): string {
    return code.startsWith('error.') ? code.slice('error.'.length) : code;
}

export function localizeAdminApiError(payload: ApiErrorPayload | undefined): string {
    if (!payload?.code) return FALLBACK_MESSAGE;
    const key = stripPrefix(payload.code);
    return ERROR_MESSAGES_VI[key] ?? FALLBACK_MESSAGE;
}

export function extractErrorMessage(error: unknown): string {
    if (isApiErrorPayload(error)) return localizeAdminApiError(error);
    if (error instanceof Error && error.message) return error.message;
    return FALLBACK_MESSAGE;
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
    return (
        typeof value === 'object' &&
        value !== null &&
        'code' in value &&
        typeof (value as { code: unknown }).code === 'string'
    );
}
