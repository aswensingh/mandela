import { baseApi } from '@/services/baseApi';

export type KnowledgeDocumentStatus = 'PROCESSING' | 'PROCESSED' | 'FAILED';

export type KnowledgeDocument = {
  id: string;
  tenantId: string;
  title: string;
  fileName: string;
  status: KnowledgeDocumentStatus;
  errorMessage: string | null;
  chunkCount: number;
  createdByUserId: string;
  // hasContent = false for rows uploaded before V14 (no source file kept).
  // The Download button is disabled with an explanatory tooltip in that case.
  hasContent: boolean;
  contentSize: number | null;
  contentType: string | null;
  createdAt: string;
  updatedAt: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListArgs = { page?: number; size?: number };

export const knowledgeApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listDocuments: builder.query<Page<KnowledgeDocument>, ListArgs | void>({
      query: (args) => ({
        url: '/knowledge-documents',
        params: { page: args?.page ?? 0, size: args?.size ?? 50 },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((d) => ({ type: 'KnowledgeDocument' as const, id: d.id })),
              { type: 'KnowledgeDocument' as const, id: 'LIST' },
            ]
          : [{ type: 'KnowledgeDocument' as const, id: 'LIST' }],
    }),
    uploadDocument: builder.mutation<KnowledgeDocument, FormData>({
      query: (form) => ({
        url: '/knowledge-documents',
        method: 'POST',
        body: form,
      }),
      invalidatesTags: [{ type: 'KnowledgeDocument', id: 'LIST' }],
    }),
    deleteDocument: builder.mutation<void, string>({
      query: (id) => ({ url: `/knowledge-documents/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'KnowledgeDocument', id },
        { type: 'KnowledgeDocument', id: 'LIST' },
      ],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListDocumentsQuery,
  useUploadDocumentMutation,
  useDeleteDocumentMutation,
} = knowledgeApi;
