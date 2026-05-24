import { baseApi } from '@/services/baseApi';

export type ImportJobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type ImportRowError = {
  rowNumber: number;
  reason: string;
  rawLine: string | null;
};

export type ImportJob = {
  id: string;
  fileName: string;
  status: ImportJobStatus;
  totalRows: number;
  processedRows: number;
  failedRows: number;
  errorLog: ImportRowError[];
  createdAt: string;
  completedAt: string | null;
};

export const importJobApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    startImport: builder.mutation<ImportJob, FormData>({
      query: (form) => ({
        url: '/customers/import',
        method: 'POST',
        body: form,
      }),
      invalidatesTags: [
        { type: 'Customer', id: 'LIST' },
        { type: 'ImportJob', id: 'LIST' },
      ],
    }),
    getImportJob: builder.query<ImportJob, string>({
      query: (id) => `/customers/import/${id}`,
      providesTags: (_res, _err, id) => [{ type: 'ImportJob', id }],
    }),
  }),
  overrideExisting: false,
});

export const { useStartImportMutation, useGetImportJobQuery } = importJobApi;
