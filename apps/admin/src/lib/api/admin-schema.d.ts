export interface paths {
  '/api/admin/me': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['AdminMeResponse'];
          };
        };
      };
    };
  };
  '/api/admin/audit/events': {
    get: {
      parameters: {
        query?: {
          actorEmail?: string;
          action?: string;
          targetKind?: string;
          targetId?: string;
          from?: string;
          to?: string;
          cursor?: string;
          limit?: number;
        };
      };
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['AdminAuditPageResponse'];
          };
        };
      };
    };
  };
  '/api/admin/admins': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['AdminUserSummaryResponse'][];
          };
        };
      };
    };
  };
  '/api/admin/grant-admin': {
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['GrantAdminRequest'];
        };
      };
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['GrantAdminResponse'];
          };
        };
      };
    };
  };
  '/api/admin/admins/{adminUserId}/revoke': {
    post: {
      parameters: {
        path: {
          adminUserId: string;
        };
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['RevokeAdminRequest'];
        };
      };
      responses: {
        204: never;
      };
    };
  };
}

export interface components {
  schemas: {
    AdminMeResponse: {
      adminUserId: string;
      email: string;
      env: 'prod' | 'staging' | 'dev';
    };
    AdminAuditPageResponse: {
      rows: components['schemas']['AdminAuditEventResponse'][];
      nextCursor?: string;
      hasNextPage: boolean;
      totalEstimate: number;
    };
    AdminAuditEventResponse: {
      auditId: string;
      chainIndex: number;
      actorEmail: string;
      action: string;
      targetKind?: string;
      targetId?: string;
      reason?: string;
      requestIp?: string;
      requestId?: string;
      createdAt: string;
    };
    AdminUserSummaryResponse: {
      adminUserId: string;
      email: string;
      status: string;
      lastUsedAt?: string;
      hasCredential: boolean;
    };
    GrantAdminRequest: {
      email: string;
    };
    GrantAdminResponse: {
      adminUserId: string;
      enrollmentUrl: string;
      expiresAt: string;
    };
    RevokeAdminRequest: {
      reason: string;
    };
  };
}
