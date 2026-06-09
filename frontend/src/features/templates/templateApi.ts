import { baseApi } from '@/services/baseApi';

export type TemplateStatus =
  | 'APPROVED'
  | 'PENDING'
  | 'REJECTED'
  | 'PAUSED'
  | 'DISABLED'
  | 'NOT_FOUND';

export type TemplateSyncResult = {
  checked: number;
  updated: number;
  notFound: number;
  metaCount: number;
  message: string;
};

export type Template = {
  id: string;
  tenantId: string;
  name: string;
  whatsappTemplateName: string;
  language: string;
  bodyPreview: string | null;
  status: TemplateStatus;
  createdAt: string;
  updatedAt: string;
};

export type CreateTemplateRequest = {
  name: string;
  whatsappTemplateName: string;
  language: string;
  bodyPreview?: string;
  status?: TemplateStatus;
};

export type UpdateTemplateRequest = {
  name?: string;
  bodyPreview?: string;
  status?: TemplateStatus;
  language?: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListTemplatesArgs = {
  page?: number;
  size?: number;
};

export const templateApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listTemplates: builder.query<Page<Template>, ListTemplatesArgs | void>({
      query: (args) => ({
        url: '/templates',
        params: {
          page: args?.page ?? 0,
          size: args?.size ?? 50,
        },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((t) => ({ type: 'Template' as const, id: t.id })),
              { type: 'Template' as const, id: 'LIST' },
            ]
          : [{ type: 'Template' as const, id: 'LIST' }],
    }),
    createTemplate: builder.mutation<Template, CreateTemplateRequest>({
      query: (body) => ({ url: '/templates', method: 'POST', body }),
      invalidatesTags: [{ type: 'Template', id: 'LIST' }],
    }),
    updateTemplate: builder.mutation<Template, { id: string; patch: UpdateTemplateRequest }>({
      query: ({ id, patch }) => ({ url: `/templates/${id}`, method: 'PATCH', body: patch }),
      invalidatesTags: (_r, _e, arg) => [
        { type: 'Template', id: arg.id },
        { type: 'Template', id: 'LIST' },
      ],
    }),
    deleteTemplate: builder.mutation<void, string>({
      query: (id) => ({ url: `/templates/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Template', id },
        { type: 'Template', id: 'LIST' },
      ],
    }),
    syncTemplates: builder.mutation<TemplateSyncResult, void>({
      query: () => ({ url: '/templates/sync', method: 'POST' }),
      // Status changes for the whole list — refetch it.
      invalidatesTags: [{ type: 'Template', id: 'LIST' }],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListTemplatesQuery,
  useCreateTemplateMutation,
  useUpdateTemplateMutation,
  useDeleteTemplateMutation,
  useSyncTemplatesMutation,
} = templateApi;
