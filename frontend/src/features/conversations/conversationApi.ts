import { baseApi } from '@/services/baseApi';

export type ConversationStatus = 'BOT_ACTIVE' | 'HUMAN_ACTIVE' | 'CLOSED';
export type MessageDirection = 'IN' | 'OUT';
export type MessageStatus = 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';
export type SenderType = 'SYSTEM' | 'AGENT' | 'BOT' | 'CUSTOMER';
export type ConversationFilter = 'ALL' | 'MINE' | 'OPEN' | 'CLOSED';

export type ConversationListItem = {
  id: string;
  status: ConversationStatus;
  assignedAgentId: string | null;
  customerId: string;
  customerPhone: string | null;
  customerName: string | null;
  lastMessageAt: string | null;
  lastMessageBody: string | null;
  lastMessageDirection: MessageDirection | null;
  lastMessageSenderType: SenderType | null;
  createdAt: string;
  handoffReason: string | null;
  handoffConfidence: number | null;
};

export type ConversationMessage = {
  id: string;
  direction: MessageDirection;
  senderType: SenderType;
  body: string;
  status: MessageStatus;
  whatsappMessageId: string | null;
  errorMessage: string | null;
  aiConfidence: number | null;
  createdAt: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type ListArgs = {
  filter?: ConversationFilter;
  page?: number;
  size?: number;
};

type MessagesArgs = {
  id: string;
  page?: number;
  size?: number;
};

type SendMessageArgs = {
  id: string;
  body: string;
};

export const conversationApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listConversations: builder.query<Page<ConversationListItem>, ListArgs | void>({
      query: (args) => ({
        url: '/conversations',
        params: {
          page: args?.page ?? 0,
          size: args?.size ?? 50,
          ...(args?.filter ? { filter: args.filter } : {}),
        },
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.content.map((c) => ({ type: 'Conversation' as const, id: c.id })),
              { type: 'Conversation' as const, id: 'LIST' },
            ]
          : [{ type: 'Conversation' as const, id: 'LIST' }],
    }),
    listMessages: builder.query<Page<ConversationMessage>, MessagesArgs>({
      query: ({ id, page, size }) => ({
        url: `/conversations/${id}/messages`,
        params: { page: page ?? 0, size: size ?? 200 },
      }),
      providesTags: (_r, _e, arg) => [{ type: 'Conversation', id: `${arg.id}-messages` }],
    }),
    takeOverConversation: builder.mutation<ConversationListItem, string>({
      query: (id) => ({
        url: `/conversations/${id}/take-over`,
        method: 'POST',
      }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Conversation', id },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
    releaseConversation: builder.mutation<ConversationListItem, string>({
      query: (id) => ({
        url: `/conversations/${id}/release`,
        method: 'POST',
      }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Conversation', id },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
    resetBotContext: builder.mutation<ConversationListItem, string>({
      query: (id) => ({
        url: `/conversations/${id}/reset-bot-context`,
        method: 'POST',
      }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Conversation', id },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
    deleteConversation: builder.mutation<void, string>({
      query: (id) => ({
        url: `/conversations/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_r, _e, id) => [
        { type: 'Conversation', id },
        { type: 'Conversation', id: `${id}-messages` },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
    sendAgentMessage: builder.mutation<ConversationMessage, SendMessageArgs>({
      query: ({ id, body }) => ({
        url: `/conversations/${id}/messages`,
        method: 'POST',
        body: { body },
      }),
      invalidatesTags: (_r, _e, arg) => [
        { type: 'Conversation', id: arg.id },
        { type: 'Conversation', id: `${arg.id}-messages` },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
  }),
  overrideExisting: false,
});

export const {
  useListConversationsQuery,
  useListMessagesQuery,
  useTakeOverConversationMutation,
  useReleaseConversationMutation,
  useResetBotContextMutation,
  useDeleteConversationMutation,
  useSendAgentMessageMutation,
} = conversationApi;
