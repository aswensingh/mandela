import { baseApi } from '@/services/baseApi';
import type { AuthUser, Credentials } from './authSlice';

type LoginRequest = { username: string; password: string };
type RefreshRequest = { refreshToken: string };

export const authApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    login: builder.mutation<Credentials, LoginRequest>({
      query: (body) => ({
        url: '/auth/login',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Me'],
    }),
    refresh: builder.mutation<Credentials, RefreshRequest>({
      query: (body) => ({
        url: '/auth/refresh',
        method: 'POST',
        body,
      }),
    }),
    logout: builder.mutation<void, RefreshRequest>({
      query: (body) => ({
        url: '/auth/logout',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Me'],
    }),
    getMe: builder.query<AuthUser, void>({
      query: () => ({ url: '/auth/me' }),
      providesTags: ['Me'],
    }),
  }),
  overrideExisting: false,
});

export const {
  useLoginMutation,
  useRefreshMutation,
  useLogoutMutation,
  useGetMeQuery,
} = authApi;
