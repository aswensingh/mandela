import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQueryWithReauth } from './baseQuery';

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  tagTypes: ['Me', 'Tenant', 'User', 'Customer', 'ImportJob', 'WhatsAppConfig', 'Template', 'Campaign', 'Conversation', 'KnowledgeDocument'],
  endpoints: () => ({}),
});
