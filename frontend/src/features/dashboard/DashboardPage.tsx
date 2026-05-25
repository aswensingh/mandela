import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  List,
  Row,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import {
  BookOutlined,
  CheckCircleOutlined,
  CommentOutlined,
  ContactsOutlined,
  FileTextOutlined,
  MessageOutlined,
  PlusOutlined,
  RocketOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { useAppSelector } from '@/app/hooks';
import { useListTenantsQuery } from '@/features/tenants/tenantApi';
import { useListCustomersQuery } from '@/features/customers/customerApi';
import { useListTemplatesQuery } from '@/features/templates/templateApi';
import { useListCampaignsQuery } from '@/features/campaigns/campaignApi';
import { useListConversationsQuery } from '@/features/conversations/conversationApi';
import { useListDocumentsQuery } from '@/features/knowledge/knowledgeApi';
import {
  useGetChatbotConfigQuery,
  useGetWhatsappConfigQuery,
} from '@/features/whatsapp/whatsappApi';

const { Title, Text, Paragraph } = Typography;

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  if (!user) return null;
  if (user.role === 'PLATFORM_ADMIN') return <PlatformAdminDashboard />;
  if (user.role === 'AGENT' || user.role === 'VIEWER') return <AgentDashboard />;
  return <TenantAdminDashboard />;
}

// ---------- Platform Admin ----------

function PlatformAdminDashboard() {
  const user = useAppSelector((s) => s.auth.user)!;
  const navigate = useNavigate();
  const { data, isLoading } = useListTenantsQuery({ size: 5, includeDeleted: false });
  const { data: withDeleted } = useListTenantsQuery({ size: 1, includeDeleted: true });

  const activeCount = data?.totalElements ?? 0;
  const totalCount = withDeleted?.totalElements ?? 0;
  const deletedCount = Math.max(0, totalCount - activeCount);

  return (
    <div>
      <Title level={3} style={{ marginTop: 0 }}>
        Welcome back, {user.fullName.split('@')[0]}.
      </Title>
      <Paragraph type="secondary">
        You're the platform operator. Manage client workspaces below.
      </Paragraph>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Active tenants"
              value={activeCount}
              prefix={<ShopOutlined />}
              loading={isLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Soft-deleted"
              value={deletedCount}
              valueStyle={{ color: deletedCount > 0 ? '#cf1322' : undefined }}
              loading={isLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card styles={{ body: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 86 } }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/app/tenants')}>
              Create Tenant
            </Button>
          </Card>
        </Col>
      </Row>

      <Card title="Recent tenants">
        {isLoading ? (
          <Skeleton active />
        ) : (data?.content?.length ?? 0) === 0 ? (
          <Text type="secondary">No tenants yet. Click Create Tenant above to start.</Text>
        ) : (
          <List
            dataSource={data!.content}
            renderItem={(t) => (
              <List.Item
                actions={[
                  <Button type="link" size="small" onClick={() => navigate('/app/tenants')}>
                    Manage
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{t.name}</Text>
                      <Tag color={t.status === 'ACTIVE' ? 'green' : 'orange'}>{t.status}</Tag>
                    </Space>
                  }
                  description={
                    <Space split="•">
                      <Text type="secondary">{t.industry ?? 'no industry'}</Text>
                      <Text type="secondary">
                        {t.admins?.length ?? 0} admin{(t.admins?.length ?? 0) === 1 ? '' : 's'}
                      </Text>
                      <Text type="secondary">
                        created {new Date(t.createdAt).toLocaleDateString()}
                      </Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  );
}

// ---------- Tenant Admin ----------

function TenantAdminDashboard() {
  const user = useAppSelector((s) => s.auth.user)!;
  const navigate = useNavigate();

  // Lightweight counts via size=1 — we only need totalElements.
  const { data: whatsappCfg, isLoading: whatsappLoading } = useGetWhatsappConfigQuery();
  const { data: chatbotCfg } = useGetChatbotConfigQuery();
  const { data: customers, isLoading: customersLoading } = useListCustomersQuery({ size: 1 });
  const { data: templates } = useListTemplatesQuery({ size: 1 });
  const { data: campaigns } = useListCampaignsQuery({ page: 0, size: 1 });
  const { data: openConvos } = useListConversationsQuery({ filter: 'OPEN', size: 1 });
  const { data: docs } = useListDocumentsQuery({ page: 0, size: 1 });

  const customerCount = customers?.totalElements ?? 0;
  const templateCount = templates?.totalElements ?? 0;
  const campaignCount = campaigns?.totalElements ?? 0;
  const openConvoCount = openConvos?.totalElements ?? 0;
  const docCount = docs?.totalElements ?? 0;

  const checklistItems = [
    {
      key: 'whatsapp',
      label: 'Configure WhatsApp credentials',
      done: !!whatsappCfg?.configured,
      cta: 'Go to WhatsApp settings',
      path: '/app/whatsapp',
    },
    {
      key: 'chatbot',
      label: 'Set the AI chatbot prompt',
      done: !!(chatbotCfg?.aiSystemPrompt && chatbotCfg.aiSystemPrompt.trim().length > 0),
      cta: 'Open chatbot settings',
      path: '/app/whatsapp',
    },
    {
      key: 'kb',
      label: 'Upload knowledge documents (FAQ, price list, …)',
      done: docCount > 0,
      cta: 'Open Knowledge Base',
      path: '/app/knowledge',
    },
    {
      key: 'customers',
      label: 'Add customers (CSV or manually)',
      done: customerCount > 0,
      cta: 'Open Customers',
      path: '/app/customers',
    },
    {
      key: 'templates',
      label: 'Create a message template',
      done: templateCount > 0,
      cta: 'Open Templates',
      path: '/app/templates',
    },
    {
      key: 'campaigns',
      label: 'Launch your first campaign',
      done: campaignCount > 0,
      cta: 'Open Campaigns',
      path: '/app/campaigns',
    },
  ];
  const doneCount = checklistItems.filter((i) => i.done).length;
  const allDone = doneCount === checklistItems.length;

  return (
    <div>
      <Title level={3} style={{ marginTop: 0 }}>
        {user.tenantName ?? 'Your workspace'}
      </Title>
      <Paragraph type="secondary">
        Welcome back, <strong>{user.fullName}</strong>. Here's the state of your workspace.
      </Paragraph>

      {!allDone && (
        <Card
          title={
            <Space>
              <span>Get started</span>
              <Tag color="blue">
                {doneCount} / {checklistItems.length} complete
              </Tag>
            </Space>
          }
          style={{ marginBottom: 24 }}
        >
          <Paragraph type="secondary" style={{ marginTop: 0 }}>
            Knock these out in order to have a fully working WhatsApp + AI setup.
          </Paragraph>
          <List
            size="small"
            dataSource={checklistItems}
            renderItem={(item) => (
              <List.Item
                actions={
                  item.done
                    ? []
                    : [
                        <Button type="link" size="small" onClick={() => navigate(item.path)}>
                          {item.cta}
                        </Button>,
                      ]
                }
              >
                <Space>
                  {item.done ? (
                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  ) : (
                    <CheckCircleOutlined style={{ color: 'rgba(0,0,0,0.15)' }} />
                  )}
                  <Text delete={item.done} type={item.done ? 'secondary' : undefined}>
                    {item.label}
                  </Text>
                </Space>
              </List.Item>
            )}
          />
        </Card>
      )}

      {whatsappLoading || customersLoading ? (
        <Skeleton active />
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="Customers"
              value={customerCount}
              icon={<ContactsOutlined />}
              onClick={() => navigate('/app/customers')}
            />
          </Col>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="Templates"
              value={templateCount}
              icon={<FileTextOutlined />}
              onClick={() => navigate('/app/templates')}
            />
          </Col>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="Campaigns"
              value={campaignCount}
              icon={<RocketOutlined />}
              onClick={() => navigate('/app/campaigns')}
            />
          </Col>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="Open chats"
              value={openConvoCount}
              icon={<CommentOutlined />}
              onClick={() => navigate('/app/conversations')}
              accent={openConvoCount > 0 ? '#1677ff' : undefined}
            />
          </Col>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="Knowledge docs"
              value={docCount}
              icon={<BookOutlined />}
              onClick={() => navigate('/app/knowledge')}
            />
          </Col>
          <Col xs={12} sm={8} md={6} lg={4}>
            <StatCard
              title="WhatsApp"
              value={whatsappCfg?.configured ? 'Connected' : 'Not set'}
              icon={<MessageOutlined />}
              onClick={() => navigate('/app/whatsapp')}
              accent={whatsappCfg?.configured ? '#52c41a' : '#cf1322'}
            />
          </Col>
        </Row>
      )}

      {!whatsappCfg?.configured && (
        <Alert
          type="warning"
          message="WhatsApp is not configured yet"
          description="Until you paste your Meta Cloud API credentials in WhatsApp Settings, you can't actually send or receive real messages. The app currently runs in mock mode."
          showIcon
          style={{ marginTop: 24 }}
          action={
            <Button size="small" onClick={() => navigate('/app/whatsapp')}>
              Configure
            </Button>
          }
        />
      )}
    </div>
  );
}

// ---------- Agent / Viewer ----------

function AgentDashboard() {
  const user = useAppSelector((s) => s.auth.user)!;
  const navigate = useNavigate();
  const { data: open, isLoading } = useListConversationsQuery({ filter: 'OPEN', size: 1 });
  const { data: mine } = useListConversationsQuery({ filter: 'MINE', size: 1 });

  return (
    <div>
      <Title level={3} style={{ marginTop: 0 }}>
        Welcome, {user.fullName.split('@')[0]}.
      </Title>
      <Paragraph type="secondary">
        {user.tenantName ?? 'Your workspace'} · You're signed in as <Tag>{user.role}</Tag>
      </Paragraph>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12}>
          <StatCard
            title="Open conversations"
            value={open?.totalElements ?? 0}
            icon={<CommentOutlined />}
            onClick={() => navigate('/app/conversations')}
            loading={isLoading}
            accent="#1677ff"
          />
        </Col>
        <Col xs={24} sm={12}>
          <StatCard
            title="Assigned to me"
            value={mine?.totalElements ?? 0}
            icon={<CommentOutlined />}
            onClick={() => navigate('/app/conversations')}
            accent="#faad14"
          />
        </Col>
      </Row>

      <Card>
        <Space direction="vertical" size={8}>
          <Text strong>What you can do here</Text>
          {user.role === 'AGENT' ? (
            <>
              <Text type="secondary">
                • Watch the Conversations page. The bot answers automatically — click <Tag>Take Over</Tag>{' '}
                whenever you want to handle a chat manually.
              </Text>
              <Text type="secondary">
                • When you're done, click <Tag>Release to Bot</Tag> and the chatbot resumes on the next inbound.
              </Text>
            </>
          ) : (
            <Text type="secondary">
              You have view-only access. You can browse Conversations, Customers, Templates, and Campaigns but
              cannot make changes.
            </Text>
          )}
          <Button
            type="primary"
            icon={<CommentOutlined />}
            onClick={() => navigate('/app/conversations')}
            style={{ marginTop: 8 }}
          >
            Open Conversations
          </Button>
        </Space>
      </Card>
    </div>
  );
}

// ---------- Shared ----------

function StatCard({
  title,
  value,
  icon,
  onClick,
  loading,
  accent,
}: {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  onClick?: () => void;
  loading?: boolean;
  accent?: string;
}) {
  return (
    <Card
      hoverable={!!onClick}
      onClick={onClick}
      styles={{ body: { padding: 16 } }}
    >
      <Statistic
        title={title}
        value={value}
        prefix={icon}
        valueStyle={accent ? { color: accent } : undefined}
        loading={loading}
      />
    </Card>
  );
}
