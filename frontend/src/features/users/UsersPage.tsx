import { useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useAppSelector } from '@/app/hooks';
import type { UserRole } from '@/features/auth/authSlice';
import {
  useCreateUserMutation,
  useDisableUserMutation,
  useListUsersQuery,
  useUpdateUserMutation,
  type TenantUser,
} from './userApi';
import ResetPasswordModal from './ResetPasswordModal';

const { Title } = Typography;

type CreateFormValues = {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
};

type EditFormValues = {
  fullName: string;
  role: UserRole;
};

const TENANT_ROLES: UserRole[] = ['TENANT_ADMIN', 'AGENT', 'VIEWER'];

const ROLE_COLORS: Record<UserRole, string> = {
  PLATFORM_ADMIN: 'gold',
  TENANT_ADMIN: 'blue',
  AGENT: 'green',
  VIEWER: 'default',
};

export default function UsersPage() {
  const callerRole = useAppSelector((state) => state.auth.user?.role);
  const isTenantAdmin = callerRole === 'TENANT_ADMIN';

  const [page, setPage] = useState(0);
  const pageSize = 50;
  const { data, isLoading, error } = useListUsersQuery({ page, size: pageSize });
  const [createUser, { isLoading: isCreating }] = useCreateUserMutation();
  const [updateUser, { isLoading: isUpdating }] = useUpdateUserMutation();
  const [disableUser, { isLoading: isDisabling }] = useDisableUserMutation();

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<CreateFormValues>();
  const [createError, setCreateError] = useState<string | null>(null);

  const [editing, setEditing] = useState<TenantUser | null>(null);
  const [editForm] = Form.useForm<EditFormValues>();
  const [editError, setEditError] = useState<string | null>(null);

  const [resetTarget, setResetTarget] = useState<TenantUser | null>(null);

  const onCreate = async (values: CreateFormValues) => {
    setCreateError(null);
    try {
      await createUser(values).unwrap();
      message.success(`User "${values.email}" created`);
      createForm.resetFields();
      setCreateOpen(false);
    } catch (err) {
      setCreateError(extractMessage(err, 'Failed to create user'));
    }
  };

  const onEdit = async (values: EditFormValues) => {
    if (!editing) return;
    setEditError(null);
    try {
      await updateUser({
        id: editing.id,
        patch: { fullName: values.fullName, role: values.role },
      }).unwrap();
      message.success('User updated');
      setEditing(null);
    } catch (err) {
      setEditError(extractMessage(err, 'Failed to update user'));
    }
  };

  const onDisable = async (record: TenantUser) => {
    try {
      await disableUser(record.id).unwrap();
      message.success(`Disabled ${record.email}`);
    } catch (err) {
      message.error(extractMessage(err, 'Failed to disable user'));
    }
  };

  const columns: ColumnsType<TenantUser> = [
    { title: 'Email', dataIndex: 'email', key: 'email' },
    { title: 'Full Name', dataIndex: 'fullName', key: 'fullName' },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: UserRole) => <Tag color={ROLE_COLORS[role]}>{role}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: TenantUser['status']) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{status}</Tag>
      ),
    },
    {
      title: 'Last Login',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (val: string | null) => (val ? new Date(val).toLocaleString() : '—'),
    },
  ];

  if (isTenantAdmin) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      render: (_v, record) => (
        <Space size={4}>
          <Button
            size="small"
            onClick={() => {
              setEditing(record);
              editForm.setFieldsValue({ fullName: record.fullName, role: record.role });
            }}
          >
            Edit
          </Button>
          <Button size="small" onClick={() => setResetTarget(record)}>
            Reset password
          </Button>
          {record.status === 'ACTIVE' && (
            <Popconfirm
              title={`Disable ${record.email}?`}
              description="Their refresh tokens will be revoked."
              okText="Disable"
              okButtonProps={{ danger: true }}
              onConfirm={() => onDisable(record)}
            >
              <Button size="small" danger loading={isDisabling}>
                Disable
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    });
  }

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>
          Users
        </Title>
        {isTenantAdmin && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            Add User
          </Button>
        )}
      </Space>

      {error && (
        <Alert
          type="error"
          message="Failed to load users"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Table<TenantUser>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        pagination={{
          current: (data?.number ?? 0) + 1,
          pageSize,
          total: data?.totalElements ?? 0,
          onChange: (p) => setPage(p - 1),
        }}
      />

      <Modal
        title="Add User"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false);
          createForm.resetFields();
          setCreateError(null);
        }}
        onOk={() => createForm.submit()}
        confirmLoading={isCreating}
        okText="Create"
        destroyOnHidden
      >
        {createError && (
          <Alert
            type="error"
            message={createError}
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Form<CreateFormValues>
          form={createForm}
          layout="vertical"
          onFinish={onCreate}
          initialValues={{ role: 'AGENT' }}
        >
          <Form.Item
            label="Email"
            name="email"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Invalid email' },
            ]}
          >
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item
            label="Full name"
            name="fullName"
            rules={[{ required: true, message: 'Full name is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[
              { required: true, message: 'Password is required' },
              { min: 8, message: 'At least 8 characters' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="Role"
            name="role"
            rules={[{ required: true, message: 'Role is required' }]}
          >
            <Select
              options={TENANT_ROLES.map((r) => ({ label: r, value: r }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? `Edit ${editing.email}` : 'Edit'}
        open={editing !== null}
        onCancel={() => {
          setEditing(null);
          setEditError(null);
        }}
        onOk={() => editForm.submit()}
        confirmLoading={isUpdating}
        okText="Save"
        destroyOnHidden
      >
        {editError && (
          <Alert
            type="error"
            message={editError}
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Form<EditFormValues> form={editForm} layout="vertical" onFinish={onEdit}>
          <Form.Item
            label="Full name"
            name="fullName"
            rules={[{ required: true, message: 'Full name is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="Role"
            name="role"
            rules={[{ required: true, message: 'Role is required' }]}
          >
            <Select options={TENANT_ROLES.map((r) => ({ label: r, value: r }))} />
          </Form.Item>
        </Form>
      </Modal>

      <ResetPasswordModal
        open={resetTarget !== null}
        userId={resetTarget?.id ?? null}
        userEmail={resetTarget?.email ?? null}
        onClose={() => setResetTarget(null)}
      />
    </div>
  );
}

function extractMessage(err: unknown, fallback: string): string {
  const apiMessage = (err as { data?: { error?: { message?: string } } })?.data?.error?.message;
  return apiMessage ?? fallback;
}
