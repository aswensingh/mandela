import { useMemo, useState } from 'react';
import {
  Avatar,
  Button,
  Drawer,
  Dropdown,
  Grid,
  Layout,
  Menu,
  Space,
  Tag,
  Typography,
  type MenuProps,
} from 'antd';
import {
  AppstoreOutlined,
  BookOutlined,
  CommentOutlined,
  ContactsOutlined,
  FileTextOutlined,
  LogoutOutlined,
  MenuOutlined,
  MessageOutlined,
  RocketOutlined,
  ShopOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { loggedOut, type UserRole } from '@/features/auth/authSlice';
import { useLogoutMutation } from '@/features/auth/authApi';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

const ROLE_COLOR: Record<UserRole, string> = {
  PLATFORM_ADMIN: 'gold',
  TENANT_ADMIN: 'blue',
  AGENT: 'green',
  VIEWER: 'default',
};

export default function AppLayout() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAppSelector((state) => state.auth.user);
  const refreshToken = useAppSelector((state) => state.auth.refreshToken);
  const [logout, { isLoading }] = useLogoutMutation();

  // AntD's responsive helper. md = 768px+. Below that we treat as mobile and
  // swap the persistent sider for a hamburger-triggered drawer.
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [drawerOpen, setDrawerOpen] = useState(false);

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

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
    setDrawerOpen(false);
  };

  // Avatar initials: prefer first letter of full name, fall back to first letter of email.
  const initials = useMemo(() => {
    const source = user?.fullName ?? user?.email ?? '?';
    const parts = source.split(/[\s@.]+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
    return (parts[0]![0]! + parts[1]![0]!).toUpperCase();
  }, [user?.fullName, user?.email]);

  const dropdownRender = () => (
    <div
      style={{
        background: '#fff',
        borderRadius: 8,
        boxShadow: '0 6px 16px rgba(0,0,0,0.08)',
        minWidth: 240,
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '12px 16px' }}>
        <Text strong style={{ display: 'block' }}>
          {user?.fullName}
        </Text>
        <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
          {user?.email}
        </Text>
        <Space size={6} style={{ marginTop: 8, flexWrap: 'wrap' }}>
          {user?.role && <Tag color={ROLE_COLOR[user.role]}>{user.role}</Tag>}
          {user?.tenantName && <Tag>{user.tenantName}</Tag>}
        </Space>
      </div>
      <div
        style={{
          borderTop: '1px solid #f0f0f0',
          padding: '8px 4px',
        }}
      >
        <Menu
          mode="vertical"
          selectable={false}
          style={{ borderInlineEnd: 'none' }}
          items={[
            {
              key: 'logout',
              icon: <LogoutOutlined />,
              label: 'Log out',
              danger: true,
              disabled: isLoading,
              onClick: handleLogout,
            },
          ]}
        />
      </div>
    </div>
  );

  const sideMenu = (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      items={menuItems}
      onClick={handleMenuClick}
      style={{ borderRight: 0, paddingTop: 16 }}
    />
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: isMobile ? '0 12px' : '0 24px',
          gap: 8,
        }}
      >
        <Space size={8} style={{ minWidth: 0 }}>
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined style={{ color: 'white', fontSize: 18 }} />}
              onClick={() => setDrawerOpen(true)}
              aria-label="Open menu"
            />
          )}
          <Title
            level={4}
            style={{
              color: 'white',
              margin: 0,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            MarketingHub
          </Title>
        </Space>
        <Dropdown
          dropdownRender={dropdownRender}
          trigger={['click']}
          placement="bottomRight"
        >
          <Space style={{ cursor: 'pointer', color: 'white', minWidth: 0 }} size={10}>
            {/* Tenant name only on tablet+ — on phone the avatar alone is enough,
                full tenant name is in the dropdown anyway. */}
            {user?.tenantName && !isMobile && (
              <Text
                style={{
                  color: 'rgba(255,255,255,0.7)',
                  maxWidth: 160,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {user.tenantName}
              </Text>
            )}
            <Avatar
              style={{ backgroundColor: '#1677ff' }}
              icon={initials === '?' ? <UserOutlined /> : undefined}
            >
              {initials === '?' ? null : initials}
            </Avatar>
          </Space>
        </Dropdown>
      </Header>
      <Layout>
        {/* Desktop / tablet: persistent sider. Mobile: hidden, replaced by Drawer. */}
        {!isMobile && (
          <Sider width={220} theme="light">
            {sideMenu}
          </Sider>
        )}
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={260}
          styles={{ body: { padding: 0 } }}
          title="MarketingHub"
        >
          {sideMenu}
        </Drawer>
        <Content style={{ padding: isMobile ? 12 : 24, minWidth: 0 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
