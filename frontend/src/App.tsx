import { Navigate, Route, Routes } from 'react-router-dom';
import LoginPage from '@/features/auth/LoginPage';
import ProtectedRoute from '@/features/auth/ProtectedRoute';
import RoleRoute from '@/features/auth/RoleRoute';
import DashboardPage from '@/features/dashboard/DashboardPage';
import TenantsPage from '@/features/tenants/TenantsPage';
import UsersPage from '@/features/users/UsersPage';
import CustomersPage from '@/features/customers/CustomersPage';
import WhatsAppSettingsPage from '@/features/whatsapp/WhatsAppSettingsPage';
import TemplatesPage from '@/features/templates/TemplatesPage';
import CampaignsPage from '@/features/campaigns/CampaignsPage';
import ConversationsPage from '@/features/conversations/ConversationsPage';
import KnowledgeBasePage from '@/features/knowledge/KnowledgeBasePage';
import AppLayout from '@/shared/AppLayout';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route element={<RoleRoute roles={['PLATFORM_ADMIN']} />}>
            <Route path="tenants" element={<TenantsPage />} />
          </Route>
          <Route element={<RoleRoute roles={['TENANT_ADMIN', 'AGENT', 'VIEWER']} />}>
            <Route path="users" element={<UsersPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="templates" element={<TemplatesPage />} />
            <Route path="campaigns" element={<CampaignsPage />} />
            <Route path="conversations" element={<ConversationsPage />} />
          </Route>
          <Route element={<RoleRoute roles={['TENANT_ADMIN']} />}>
            <Route path="whatsapp" element={<WhatsAppSettingsPage />} />
            <Route path="knowledge" element={<KnowledgeBasePage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  );
}
