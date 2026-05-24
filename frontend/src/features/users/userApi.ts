import { baseApi } from '@/services/baseApi';
import type { UserRole } from '@/features/auth/authSlice';

export type UserStatus = 'ACTIVE' | 'DISABLED';

export type TenantUser = {
  id: string;
  tenantId: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  lastLoginAt: string | null;
  createdAt: string;
};

export type CreateUserRequest = {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
};

export type UpdateUserRequest = {
  fullName?: string;
  role?: UserRole;
  status?: UserStatus;
};

export type ResetPasswordArgs = {
  id: string;
  newPassword?: string | null; // null/undefined = generate
};

export type ResetPasswordResponse = {
  userId: string;
  email: string;
  newPassword: string;
  generated: boolean;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListUsersArgs = {
  page?: number;
  size?: number;
};

export const userApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listUsers: builder.query<Page<TenantUser>, ListUsersArgs | void>({
      query: (args) => ({
        url: '/users',
        params: {
          page: args?.page ?? 0,
          size: args?.size ?? 50,
        },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((u) => ({ type: 'User' as const, id: u.id })),
              { type: 'User' as const, id: 'LIST' },
            ]
          : [{ type: 'User' as const, id: 'LIST' }],
    }),
    createUser: builder.mutation<TenantUser, CreateUserRequest>({
      query: (body) => ({
        url: '/users',
        method: 'POST',
        body,
      }),
      invalidatesTags: [{ type: 'User', id: 'LIST' }],
    }),
    updateUser: builder.mutation<TenantUser, { id: string; patch: UpdateUserRequest }>({
      query: ({ id, patch }) => ({
        url: `/users/${id}`,
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (_res, _err, arg) => [
        { type: 'User', id: arg.id },
        { type: 'User', id: 'LIST' },
      ],
    }),
    disableUser: builder.mutation<TenantUser, string>({
      query: (id) => ({
        url: `/users/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_res, _err, id) => [
        { type: 'User', id },
        { type: 'User', id: 'LIST' },
      ],
    }),
    resetUserPassword: builder.mutation<ResetPasswordResponse, ResetPasswordArgs>({
      query: ({ id, newPassword }) => ({
        url: `/users/${id}/reset-password`,
        method: 'POST',
        body: { newPassword: newPassword ?? null },
      }),
      // No invalidation — the user row itself is unchanged from the list's perspective.
    }),
  }),
  overrideExisting: false,
});

export const {
  useListUsersQuery,
  useCreateUserMutation,
  useUpdateUserMutation,
  useDisableUserMutation,
  useResetUserPasswordMutation,
} = userApi;
