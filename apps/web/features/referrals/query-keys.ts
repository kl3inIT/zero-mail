export const referralKeys = {
  all: ['referrals'] as const,
  me: () => [...referralKeys.all, 'me'] as const,
};
