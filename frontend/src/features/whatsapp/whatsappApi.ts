import { baseApi } from '@/services/baseApi';

export type WhatsAppConfigStatus = {
  configured: boolean;
  phoneNumberId: string | null;
  tokenLastFour: string | null;
};

export type UpdateWhatsAppConfigRequest = {
  phoneNumberId: string;
  accessToken: string;
};

export type SendTestRequest = {
  toE164: string;
  body: string;
};

export type SendTestResponse = {
  id: string;
  tenantId: string;
  customerId: string | null;
  direction: 'OUT' | 'IN';
  senderType: 'SYSTEM' | 'AGENT' | 'BOT' | 'CUSTOMER';
  body: string;
  whatsappMessageId: string | null;
  status: 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ConnectionTestResponse = {
  ok: boolean;
  displayPhoneNumber: string | null;
  verifiedName: string | null;
  qualityRating: string | null;
  status: number | null;
  error: string | null;
};

export type ChatbotConfig = {
  aiSystemPrompt: string | null;
  handoffConfidenceThreshold: number | null;
};

export type UpdateChatbotConfigRequest = {
  aiSystemPrompt?: string | null;
  handoffConfidenceThreshold?: number | null;
};

export const whatsappApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getWhatsappConfig: builder.query<WhatsAppConfigStatus, void>({
      query: () => '/tenants/me/whatsapp',
      providesTags: [{ type: 'WhatsAppConfig', id: 'ME' }],
    }),
    updateWhatsappConfig: builder.mutation<WhatsAppConfigStatus, UpdateWhatsAppConfigRequest>({
      query: (body) => ({
        url: '/tenants/me/whatsapp',
        method: 'PUT',
        body,
      }),
      invalidatesTags: [{ type: 'WhatsAppConfig', id: 'ME' }],
    }),
    testWhatsappConnection: builder.mutation<ConnectionTestResponse, void>({
      query: () => ({
        url: '/tenants/me/whatsapp/test',
        method: 'POST',
      }),
      // No tag invalidation — this is a diagnostic; nothing changes server-side.
    }),
    sendTestMessage: builder.mutation<SendTestResponse, SendTestRequest>({
      query: (body) => ({
        url: '/whatsapp/send-test',
        method: 'POST',
        body,
      }),
    }),
    getChatbotConfig: builder.query<ChatbotConfig, void>({
      query: () => '/tenants/me/chatbot',
      providesTags: [{ type: 'WhatsAppConfig', id: 'CHATBOT' }],
    }),
    updateChatbotConfig: builder.mutation<ChatbotConfig, UpdateChatbotConfigRequest>({
      query: (body) => ({
        url: '/tenants/me/chatbot',
        method: 'PUT',
        body,
      }),
      invalidatesTags: [{ type: 'WhatsAppConfig', id: 'CHATBOT' }],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetWhatsappConfigQuery,
  useUpdateWhatsappConfigMutation,
  useTestWhatsappConnectionMutation,
  useSendTestMessageMutation,
  useGetChatbotConfigQuery,
  useUpdateChatbotConfigMutation,
} = whatsappApi;
