import { baseApi } from '@/services/baseApi';

export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';

export type TenantAdmin = {
  id: string;
  email: string;
};

export type Tenant = {
  id: string;
  name: string;
  industry: string | null;
  status: TenantStatus;
  createdAt: string;
  updatedAt: string;
  admins: TenantAdmin[];
};

export type CreateTenantRequest = {
  name: string;
  industry?: string;
  initialAdminEmail: string;
  initialAdminPassword: string;
};

export type CreateTenantResponse = {
  tenant: Tenant;
  initialAdmin: {
    id: string;
    email: string;
    fullName: string;
    tenantId: string;
    role: 'TENANT_ADMIN';
  };
};

export type UpdateTenantRequest = {
  name?: string;
  industry?: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListTenantsArgs = {
  page?: number;
  size?: number;
  includeDeleted?: boolean;
};

type PurgeTenantArgs = {
  id: string;
  confirmName: string;
};

export const tenantApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listTenants: builder.query<Page<Tenant>, ListTenantsArgs | void>({
      query: (args) => ({
        url: '/platform/tenants',
        params: {
          page: args?.page ?? 0,
          size: args?.size ?? 20,
          includeDeleted: args?.includeDeleted ? 'true' : 'false',
        },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((t) => ({ type: 'Tenant' as const, id: t.id })),
              { type: 'Tenant' as const, id: 'LIST' },
            ]
          : [{ type: 'Tenant' as const, id: 'LIST' }],
    }),
    createTenant: builder.mutation<CreateTenantResponse, CreateTenantRequest>({
      query: (body) => ({
        url: '/platform/tenants',
        method: 'POST',
        body,
      }),
      invalidatesTags: [{ type: 'Tenant', id: 'LIST' }],
    }),
    updateTenant: builder.mutation<Tenant, { id: string; patch: UpdateTenantRequest }>({
      query: ({ id, patch }) => ({
        url: `/platform/tenants/${id}`,
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (_res, _err, arg) => [
        { type: 'Tenant', id: arg.id },
        { type: 'Tenant', id: 'LIST' },
      ],
    }),
    suspendTenant: builder.mutation<Tenant, string>({
      query: (id) => ({
        url: `/platform/tenants/${id}/suspend`,
        method: 'POST',
      }),
      invalidatesTags: (_res, _err, id) => [
        { type: 'Tenant', id },
        { type: 'Tenant', id: 'LIST' },
      ],
    }),
    unsuspendTenant: builder.mutation<Tenant, string>({
      query: (id) => ({
        url: `/platform/tenants/${id}/unsuspend`,
        method: 'POST',
      }),
      invalidatesTags: (_res, _err, id) => [
        { type: 'Tenant', id },
        { type: 'Tenant', id: 'LIST' },
      ],
    }),
    softDeleteTenant: builder.mutation<Tenant, string>({
      query: (id) => ({
        url: `/platform/tenants/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_res, _err, id) => [
        { type: 'Tenant', id },
        { type: 'Tenant', id: 'LIST' },
      ],
    }),
    purgeTenant: builder.mutation<void, PurgeTenantArgs>({
      query: ({ id, confirmName }) => ({
        url: `/platform/tenants/${id}/purge`,
        method: 'DELETE',
        body: { confirmName },
      }),
      invalidatesTags: (_res, _err, arg) => [
        { type: 'Tenant', id: arg.id },
        { type: 'Tenant', id: 'LIST' },
      ],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListTenantsQuery,
  useCreateTenantMutation,
  useUpdateTenantMutation,
  useSuspendTenantMutation,
  useUnsuspendTenantMutation,
  useSoftDeleteTenantMutation,
  usePurgeTenantMutation,
} = tenantApi;
