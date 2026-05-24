import { useMemo } from 'react';
import { Button, Layout, Menu, Typography, type MenuProps } from 'antd';
import {
  AppstoreOutlined,
  BookOutlined,
  CommentOutlined,
  ContactsOutlined,
  FileTextOutlined,
  MessageOutlined,
  RocketOutlined,
  ShopOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { loggedOut } from '@/features/auth/authSlice';
import { useLogoutMutation } from '@/features/auth/authApi';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

export default function AppLayout() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAppSelector((state) => state.auth.user);
  const refreshToken = useAppSelector((state) => state.auth.refreshToken);
  const [logout, { isLoading }] = useLogoutMutation();

  const menuItems = useMemo<MenuProps['items']>(() => {
    const items: MenuProps['items'] = [
      { key: '/app', icon: <AppstoreOutlined />, label: 'Home' },
    ];
    if (user?.role === 'PLATFORM_ADMIN') {
      items.push({ key: '/app/tenants', icon: <ShopOutlined />, label: 'Tenants' });
    }
    if (
      user?.role === 'TENANT_ADMIN'
      || user?.role === 'AGENT'
      || user?.role === 'VIEWER'
    ) {
      items.push({ key: '/app/users', icon: <TeamOutlined />, label: 'Users' });
      items.push({ key: '/app/customers', icon: <ContactsOutlined />, label: 'Customers' });
      items.push({ key: '/app/templates', icon: <FileTextOutlined />, label: 'Templates' });
      items.push({ key: '/app/campaigns', icon: <RocketOutlined />, label: 'Campaigns' });
      items.push({ key: '/app/conversations', icon: <CommentOutlined />, label: 'Conversations' });
    }
    if (user?.role === 'TENANT_ADMIN') {
      items.push({ key: '/app/whatsapp', icon: <MessageOutlined />, label: 'WhatsApp' });
      items.push({ key: '/app/knowledge', icon: <BookOutlined />, label: 'Knowledge Base' });
    }
    return items;
  }, [user?.role]);

  const selectedKey = useMemo(() => {
    if (location.pathname.startsWith('/app/tenants')) return '/app/tenants';
    if (location.pathname.startsWith('/app/users')) return '/app/users';
    if (location.pathname.startsWith('/app/customers')) return '/app/customers';
    if (location.pathname.startsWith('/app/templates')) return '/app/templates';
    if (location.pathname.startsWith('/app/campaigns')) return '/app/campaigns';
    if (location.pathname.startsWith('/app/conversations')) return '/app/conversations';
    if (location.pathname.startsWith('/app/whatsapp')) return '/app/whatsapp';
    if (location.pathname.startsWith('/app/knowledge')) return '/app/knowledge';
    return '/app';
  }, [location.pathname]);

  const handleLogout = async () => {
    if (refreshToken) {
      try {
        await logout({ refreshToken }).unwrap();
      } catch {
        // ignore server errors on logout — clear client state regardless
      }
    }
    dispatch(loggedOut());
    navigate('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Title level={4} style={{ color: 'white', margin: 0 }}>
          MarketingHub
        </Title>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          {user && <Text style={{ color: 'white' }}>{user.fullName}</Text>}
          <Button onClick={handleLogout} loading={isLoading}>
            Log out
          </Button>
        </div>
      </Header>
      <Layout>
        <Sider width={220} theme="light" breakpoint="md" collapsedWidth="0">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ borderRight: 0, paddingTop: 16 }}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
