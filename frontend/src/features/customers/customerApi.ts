import { baseApi } from '@/services/baseApi';

export type OptInStatus = 'UNKNOWN' | 'OPTED_IN' | 'OPTED_OUT';

export type Customer = {
  id: string;
  tenantId: string;
  phoneE164: string;
  fullName: string | null;
  tags: string[];
  optInStatus: OptInStatus;
  customAttributes: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type CreateCustomerRequest = {
  phoneE164: string;
  fullName?: string;
  tags?: string[];
  optInStatus?: OptInStatus;
  customAttributes?: Record<string, unknown>;
};

export type UpdateCustomerRequest = Partial<CreateCustomerRequest>;

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListCustomersArgs = {
  page?: number;
  size?: number;
  search?: string;
  tag?: string;
  optInStatus?: OptInStatus;
};

export const customerApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listCustomers: builder.query<Page<Customer>, ListCustomersArgs | void>({
      query: (args) => ({
        url: '/customers',
        params: {
          page: args?.page ?? 0,
          size: args?.size ?? 50,
          ...(args?.search ? { search: args.search } : {}),
          ...(args?.tag ? { tag: args.tag } : {}),
          ...(args?.optInStatus ? { optInStatus: args.optInStatus } : {}),
        },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((c) => ({ type: 'Customer' as const, id: c.id })),
              { type: 'Customer' as const, id: 'LIST' },
            ]
          : [{ type: 'Customer' as const, id: 'LIST' }],
    }),
    createCustomer: builder.mutation<Customer, CreateCustomerRequest>({
      query: (body) => ({
        url: '/customers',
        method: 'POST',
        body,
      }),
      invalidatesTags: [{ type: 'Customer', id: 'LIST' }],
    }),
    updateCustomer: builder.mutation<Customer, { id: string; patch: UpdateCustomerRequest }>({
      query: ({ id, patch }) => ({
        url: `/customers/${id}`,
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (_res, _err, arg) => [
        { type: 'Customer', id: arg.id },
        { type: 'Customer', id: 'LIST' },
      ],
    }),
    deleteCustomer: builder.mutation<void, string>({
      query: (id) => ({
        url: `/customers/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_res, _err, id) => [
        { type: 'Customer', id },
        { type: 'Customer', id: 'LIST' },
      ],
    }),
    bulkDeleteCustomers: builder.mutation<{ deleted: number }, string[]>({
      query: (ids) => ({
        url: '/customers/bulk-delete',
        method: 'POST',
        body: { ids },
      }),
      invalidatesTags: (_res, _err, ids) => [
        ...ids.map((id) => ({ type: 'Customer' as const, id })),
        { type: 'Customer' as const, id: 'LIST' },
      ],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListCustomersQuery,
  useCreateCustomerMutation,
  useUpdateCustomerMutation,
  useDeleteCustomerMutation,
  useBulkDeleteCustomersMutation,
} = customerApi;
