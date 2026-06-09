import { baseApi } from '@/services/baseApi';

export type CampaignStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'SENDING'
  | 'SENT'
  | 'FAILED'
  | 'CANCELLED';

export type CampaignRecipientStatus =
  | 'PENDING'
  | 'SENT'
  | 'DELIVERED'
  | 'READ'
  | 'FAILED';

export type CampaignSendMode = 'TEMPLATE' | 'FREE_TEXT';

export type Campaign = {
  id: string;
  tenantId: string;
  name: string;
  status: CampaignStatus;
  sendMode: CampaignSendMode;
  templateId: string | null;
  templateName: string | null;
  bodyText: string | null;
  scheduledAt: string | null;
  createdByUserId: string;
  startedAt: string | null;
  completedAt: string | null;
  recipientCount: number;
  sentCount: number;
  failedCount: number;
  createdAt: string;
  updatedAt: string;
};

export type Recipient = {
  id: string;
  campaignId: string;
  customerId: string;
  customerPhone: string | null;
  customerName: string | null;
  status: CampaignRecipientStatus;
  errorMessage: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateCampaignRequest = {
  name: string;
  sendMode: CampaignSendMode;
  templateId?: string | null;
  bodyText?: string | null;
  scheduledAt?: string | null;
  customerIds: string[];
};

export type UpdateCampaignRequest = {
  name?: string;
  scheduledAt?: string | null;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListArgs = {
  page?: number;
  size?: number;
};

export const campaignApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listCampaigns: builder.query<Page<Campaign>, ListArgs | void>({
      query: (args) => ({
        url: '/campaigns',
        params: { page: args?.page ?? 0, size: args?.size ?? 50 },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((c) => ({ type: 'Campaign' as const, id: c.id })),
              { type: 'Campaign' as const, id: 'LIST' },
            ]
          : [{ type: 'Campaign' as const, id: 'LIST' }],
    }),
    getCampaign: builder.query<Campaign, string>({
      query: (id) => `/campaigns/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Campaign', id }],
    }),
    listRecipients: builder.query<Page<Recipient>, { id: string; page?: number; size?: number }>({
      query: ({ id, page, size }) => ({
        url: `/campaigns/${id}/recipients`,
        params: { page: page ?? 0, size: size ?? 100 },
      }),
      providesTags: (_r, _e, arg) => [{ type: 'Campaign', id: `${arg.id}-recipients` }],
    }),
    createCampaign: builder.mutation<Campaign, CreateCampaignRequest>({
      query: (body) => ({ url: '/campaigns', method: 'POST', body }),
      invalidatesTags: [{ type: 'Campaign', id: 'LIST' }],
    }),
    updateCampaign: builder.mutation<Campaign, { id: string; patch: UpdateCampaignRequest }>({
      query: ({ id, patch }) => ({ url: `/campaigns/${id}`, method: 'PATCH', body: patch }),
      invalidatesTags: (_r, _e, arg) => [
        { type: 'Campaign', id: arg.id },
        { type: 'Campaign', id: 'LIST' },
      ],
    }),
    cancelCampaign: builder.mutation<Campaign, string>({
      query: (id) => ({ url: `/campaigns/${id}/cancel`, method: 'POST' }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Campaign', id },
        { type: 'Campaign', id: 'LIST' },
      ],
    }),
    launchCampaign: builder.mutation<Campaign, string>({
      query: (id) => ({ url: `/campaigns/${id}/launch`, method: 'POST' }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Campaign', id },
        { type: 'Campaign', id: 'LIST' },
        { type: 'Campaign', id: `${id}-recipients` },
      ],
    }),
    deleteCampaign: builder.mutation<void, string>({
      query: (id) => ({ url: `/campaigns/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Campaign', id },
        { type: 'Campaign', id: 'LIST' },
      ],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListCampaignsQuery,
  useGetCampaignQuery,
  useListRecipientsQuery,
  useCreateCampaignMutation,
  useUpdateCampaignMutation,
  useCancelCampaignMutation,
  useLaunchCampaignMutation,
  useDeleteCampaignMutation,
} = campaignApi;
