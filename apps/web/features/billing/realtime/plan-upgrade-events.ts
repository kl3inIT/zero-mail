export type PlanUpgradePaymentCompletedMessage = {
  type: 'PLAN_UPGRADE_PAYMENT_COMPLETED';
  bankTransferIntentId: string;
  bankTransferCode: string;
  planCode: string;
  provider: string;
  amountVnd: number;
  currency: string;
  paidAt: string;
};

export function parsePlanUpgradePaymentCompletedMessage(
  body: string,
): PlanUpgradePaymentCompletedMessage | null {
  try {
    const parsed = JSON.parse(body) as Partial<PlanUpgradePaymentCompletedMessage>;
    if (
      parsed.type !== 'PLAN_UPGRADE_PAYMENT_COMPLETED' ||
      typeof parsed.bankTransferIntentId !== 'string' ||
      typeof parsed.bankTransferCode !== 'string' ||
      typeof parsed.planCode !== 'string' ||
      typeof parsed.provider !== 'string' ||
      typeof parsed.amountVnd !== 'number' ||
      typeof parsed.currency !== 'string' ||
      typeof parsed.paidAt !== 'string'
    ) {
      return null;
    }

    return {
      type: parsed.type,
      bankTransferIntentId: parsed.bankTransferIntentId,
      bankTransferCode: parsed.bankTransferCode,
      planCode: parsed.planCode,
      provider: parsed.provider,
      amountVnd: parsed.amountVnd,
      currency: parsed.currency,
      paidAt: parsed.paidAt,
    };
  } catch {
    return null;
  }
}
