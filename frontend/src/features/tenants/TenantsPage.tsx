import { useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { KeyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import ResetPasswordModal from '@/features/users/ResetPasswordModal';
import {
  useCreateTenantMutation,
  useListTenantsQuery,
  usePurgeTenantMutation,
  useSoftDeleteTenantMutation,
  useSuspendTenantMutation,
  useUnsuspendTenantMutation,
  type Tenant,
  type TenantAdmin,
  type TenantStatus,
} from './tenantApi';

const { Title, Text } = Typography;

type CreateFormValues = {
  name: string;
  industry?: string;
  initialAdminUsername: string;
  initialAdminPassword: string;
};

const STATUS_COLOR: Record<TenantStatus, string> = {
  ACTIVE: 'green',
  SUSPENDED: 'orange',
  DELETED: 'red',
};

function extractError(err: unknown, fallback: string): string {
  return (
    (err as { data?: { error?: { message?: string } } })?.data?.error?.message ?? fallback
  );
}

export default function TenantsPage() {
  const [page, setPage] = useState(0);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const pageSize = 20;
  const { data, isLoading, error } = useListTenantsQuery({
    page,
    size: pageSize,
    includeDeleted,
  });
  const [createTenant, { isLoading: isCreating }] = useCreateTenantMutation();
  const [suspendTenant, { isLoading: isSuspending }] = useSuspendTenantMutation();
  const [unsuspendTenant, { isLoading: isUnsuspending }] = useUnsuspendTenantMutation();
  const [softDeleteTenant, { isLoading: isDeleting }] = useSoftDeleteTenantMutation();
  const [purgeTenant, { isLoading: isPurging }] = usePurgeTenantMutation();

  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<CreateFormValues>();
  const [createError, setCreateError] = useState<string | null>(null);

  // Purge modal state — separate from the create form.
  const [purgeTarget, setPurgeTarget] = useState<Tenant | null>(null);
  const [purgeConfirmInput, setPurgeConfirmInput] = useState('');
  const [purgeError, setPurgeError] = useState<string | null>(null);

  // Reset-password modal state — opened from the Admins column.
  const [resetAdmin, setResetAdmin] = useState<TenantAdmin | null>(null);

  const onCreate = async (values: CreateFormValues) => {
    setCreateError(null);
    try {
      await createTenant({
        name: values.name,
        industry: values.industry,
        initialAdminUsername: values.initialAdminUsername,
        initialAdminPassword: values.initialAdminPassword,
      }).unwrap();
      message.success(`Tenant "${values.name}" created`);
      form.resetFields();
      setModalOpen(false);
    } catch (err) {
      setCreateError(extractError(err, 'Failed to create tenant'));
    }
  };

  const onSoftDelete = async (tenant: Tenant) => {
    try {
      await softDeleteTenant(tenant.id).unwrap();
      message.success(`Tenant "${tenant.name}" deleted (data preserved — purge to remove permanently)`);
    } catch (err) {
      message.error(extractError(err, 'Failed to delete tenant'));
    }
  };

  const openPurgeModal = (tenant: Tenant) => {
    setPurgeTarget(tenant);
    setPurgeConfirmInput('');
    setPurgeError(null);
  };

  const onPurgeConfirm = async () => {
    if (!purgeTarget) return;
    setPurgeError(null);
    try {
      await purgeTenant({
        id: purgeTarget.id,
        confirmName: purgeConfirmInput,
      }).unwrap();
      message.success(`Tenant "${purgeTarget.name}" permanently purged`);
      setPurgeTarget(null);
      setPurgeConfirmInput('');
    } catch (err) {
      setPurgeError(extractError(err, 'Failed to purge tenant'));
    }
  };

  const columns: ColumnsType<Tenant> = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Industry',
      dataIndex: 'industry',
      key: 'industry',
      render: (val: string | null) => val ?? '—',
    },
    {
      title: 'Admins',
      key: 'admins',
      render: (_v, record) => {
        if (!record.admins || record.admins.length === 0) {
          return <Text type="secondary">— none —</Text>;
        }
        return (
          <Space direction="vertical" size={2}>
            {record.admins.map((a) => (
              <Space key={a.id} size={4}>
                <Text>{a.username}</Text>
                <Tooltip title={`Reset password for ${a.username}`}>
                  <Button
                    type="text"
                    size="small"
                    icon={<KeyOutlined />}
                    onClick={() => setResetAdmin(a)}
                  />
                </Tooltip>
              </Space>
            ))}
          </Space>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: TenantStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val: string) => new Date(val).toLocaleString(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_value, record) => {
        if (record.status === 'DELETED') {
          // Soft-deleted: the only meaningful action is to purge permanently.
          return (
            <Button
              size="small"
              danger
              loading={isPurging}
              onClick={() => openPurgeModal(record)}
            >
              Purge permanently
            </Button>
          );
        }
        return (
          <Space size={4}>
            {record.status === 'ACTIVE' ? (
              <Button
                size="small"
                loading={isSuspending}
                onClick={() => suspendTenant(record.id)}
              >
                Suspend
              </Button>
            ) : (
              <Button
                size="small"
                loading={isUnsuspending}
                onClick={() => unsuspendTenant(record.id)}
              >
                Unsuspend
              </Button>
            )}
            <Popconfirm
              title={`Delete tenant "${record.name}"?`}
              description="Hides it from this list and blocks login. Data is preserved — you can still purge it permanently later."
              okText="Delete"
              okButtonProps={{ danger: true }}
              cancelText="Cancel"
              onConfirm={() => onSoftDelete(record)}
            >
              <Button size="small" danger loading={isDeleting}>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>
          Tenants
        </Title>
        <Space>
          <Checkbox
            checked={includeDeleted}
            onChange={(e) => {
              setIncludeDeleted(e.target.checked);
              setPage(0);
            }}
          >
            Show deleted
          </Checkbox>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            Create Tenant
          </Button>
        </Space>
      </Space>

      {error && (
        <Alert
          type="error"
          message="Failed to load tenants"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Table<Tenant>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: (data?.number ?? 0) + 1,
          pageSize,
          total: data?.totalElements ?? 0,
          onChange: (p) => setPage(p - 1),
        }}
      />

      <Modal
        title="Create Tenant"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
          setCreateError(null);
        }}
        onOk={() => form.submit()}
        confirmLoading={isCreating}
        okText="Create"
        destroyOnHidden
      >
        {createError && (
          <Alert type="error" message={createError} style={{ marginBottom: 12 }} showIcon />
        )}
        <Form<CreateFormValues> form={form} layout="vertical" onFinish={onCreate}>
          <Form.Item
            label="Tenant name"
            name="name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="Industry" name="industry">
            <Input placeholder="dental, restaurant, casino, ..." />
          </Form.Item>
          <Form.Item
            label="Initial admin username"
            name="initialAdminUsername"
            rules={[{ required: true, message: 'Username is required' }]}
          >
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item
            label="Initial admin password"
            name="initialAdminPassword"
            rules={[
              { required: true, message: 'Password is required' },
              { min: 8, message: 'At least 8 characters' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Purge tenant permanently?"
        open={!!purgeTarget}
        onCancel={() => {
          setPurgeTarget(null);
          setPurgeConfirmInput('');
          setPurgeError(null);
        }}
        onOk={onPurgeConfirm}
        confirmLoading={isPurging}
        okText="Purge"
        okButtonProps={{
          danger: true,
          disabled: !purgeTarget || purgeConfirmInput !== purgeTarget.name,
        }}
        destroyOnHidden
      >
        <Alert
          type="warning"
          message="This is irreversible."
          description="All users, customers, conversations, messages, templates, campaigns, knowledge documents and chat config for this tenant will be permanently deleted."
          style={{ marginBottom: 16 }}
          showIcon
        />
        <Text>
          Type the tenant name <Text strong>{purgeTarget?.name}</Text> below to confirm:
        </Text>
        <Input
          value={purgeConfirmInput}
          onChange={(e) => setPurgeConfirmInput(e.target.value)}
          placeholder={purgeTarget?.name}
          style={{ marginTop: 8 }}
          autoFocus
        />
        {purgeError && (
          <Alert
            type="error"
            message={purgeError}
            style={{ marginTop: 12 }}
            showIcon
          />
        )}
      </Modal>

      <ResetPasswordModal
        open={resetAdmin !== null}
        userId={resetAdmin?.id ?? null}
        username={resetAdmin?.username ?? null}
        onClose={() => setResetAdmin(null)}
      />
    </div>
  );
}
