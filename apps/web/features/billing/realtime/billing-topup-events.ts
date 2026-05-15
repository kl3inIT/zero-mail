export type BillingTopupCreditedMessage = {
  type: 'TOPUP_CREDITED';
  orderCode: string;
  packageCode: string | null;
  packageName: string | null;
  amountVnd: number;
  creditAmount: number;
  creditedAt: string;
};

export function parseBillingTopupCreditedMessage(body: string): BillingTopupCreditedMessage | null {
  try {
    const parsed = JSON.parse(body) as Partial<BillingTopupCreditedMessage>;
    if (
      parsed.type !== 'TOPUP_CREDITED' ||
      typeof parsed.orderCode !== 'string' ||
      !isNullableString(parsed.packageCode) ||
      !isNullableString(parsed.packageName) ||
      typeof parsed.amountVnd !== 'number' ||
      typeof parsed.creditAmount !== 'number' ||
      typeof parsed.creditedAt !== 'string'
    ) {
      return null;
    }
    return {
      type: parsed.type,
      orderCode: parsed.orderCode,
      packageCode: parsed.packageCode ?? null,
      packageName: parsed.packageName ?? null,
      amountVnd: parsed.amountVnd,
      creditAmount: parsed.creditAmount,
      creditedAt: parsed.creditedAt,
    };
  } catch {
    return null;
  }
}

function isNullableString(value: unknown): value is string | null | undefined {
  return value === null || value === undefined || typeof value === 'string';
}
