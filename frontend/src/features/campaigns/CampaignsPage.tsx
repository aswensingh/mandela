import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
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
import { PlusOutlined, RocketOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import TableEmpty from '@/shared/TableEmpty';
import {
  useCancelCampaignMutation,
  useCreateCampaignMutation,
  useDeleteCampaignMutation,
  useLaunchCampaignMutation,
  useListCampaignsQuery,
  useListRecipientsQuery,
  type Campaign,
  type CampaignStatus,
} from './campaignApi';
import { useListCustomersQuery, type Customer } from '@/features/customers/customerApi';
import { useListTemplatesQuery, type Template } from '@/features/templates/templateApi';

const { Title, Paragraph, Text } = Typography;

const STATUS_COLOR: Record<CampaignStatus, string> = {
  DRAFT: 'default',
  SCHEDULED: 'blue',
  SENDING: 'orange',
  SENT: 'green',
  FAILED: 'red',
  CANCELLED: 'magenta',
};

type CreateFormValues = {
  name: string;
  templateId: string;
  scheduledAt?: Dayjs;
};

export default function CampaignsPage() {
  const [page, setPage] = useState(0);
  const pageSize = 50;
  const { data, isLoading, error } = useListCampaignsQuery(
    { page, size: pageSize },
    {
      // Auto-refresh while any campaign in the page is still SENDING so the user
      // sees recipientCount → sentCount progress without manual reload.
      pollingInterval: 2000,
      refetchOnMountOrArgChange: true,
    },
  );

  const [createCampaign, { isLoading: isCreating }] = useCreateCampaignMutation();
  const [cancelCampaign, { isLoading: isCancelling }] = useCancelCampaignMutation();
  const [launchCampaign, { isLoading: isLaunching }] = useLaunchCampaignMutation();
  const [deleteCampaign, { isLoading: isDeleting }] = useDeleteCampaignMutation();

  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<CreateFormValues>();
  const [createError, setCreateError] = useState<string | null>(null);
  const [selectedCustomerIds, setSelectedCustomerIds] = useState<string[]>([]);

  const [recipientsForCampaign, setRecipientsForCampaign] = useState<Campaign | null>(null);

  const { data: customersPage, isLoading: isLoadingCustomers } = useListCustomersQuery(
    { page: 0, size: 200 },
    { skip: !createOpen },
  );
  const { data: templatesPage } = useListTemplatesQuery(
    { page: 0, size: 200 },
    { skip: !createOpen },
  );

  const openCreate = () => {
    form.resetFields();
    setSelectedCustomerIds([]);
    setCreateError(null);
    setCreateOpen(true);
  };

  const onCreate = async (values: CreateFormValues) => {
    setCreateError(null);
    if (selectedCustomerIds.length === 0) {
      setCreateError('Select at least one customer');
      return;
    }
    try {
      await createCampaign({
        name: values.name,
        templateId: values.templateId,
        scheduledAt: values.scheduledAt ? values.scheduledAt.toISOString() : null,
        customerIds: selectedCustomerIds,
      }).unwrap();
      message.success('Campaign created (DRAFT)');
      setCreateOpen(false);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to create campaign';
      setCreateError(apiMessage);
    }
  };

  const onCancel = async (c: Campaign) => {
    try {
      await cancelCampaign(c.id).unwrap();
      message.success('Campaign cancelled');
    } catch (err) {
      message.error(
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
          'Failed to cancel',
      );
    }
  };

  const onDelete = async (c: Campaign) => {
    try {
      await deleteCampaign(c.id).unwrap();
      message.success(`Deleted ${c.name}`);
    } catch (err) {
      message.error(
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
          'Failed to delete',
      );
    }
  };

  const onLaunch = async (c: Campaign) => {
    try {
      await launchCampaign(c.id).unwrap();
      message.success(`Launched ${c.name} — sending ${c.recipientCount} messages`);
    } catch (err) {
      message.error(
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
          'Failed to launch',
      );
    }
  };

  const columns: ColumnsType<Campaign> = useMemo(
    () => [
      { title: 'Name', dataIndex: 'name', key: 'name' },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (s: CampaignStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
      },
      {
        title: 'Template',
        dataIndex: 'templateName',
        key: 'templateName',
        render: (v: string | null) => v ?? '—',
      },
      {
        title: 'Progress',
        key: 'progress',
        render: (_v, record) =>
          `${record.sentCount + record.failedCount}/${record.recipientCount}`
          + (record.failedCount > 0 ? ` (${record.failedCount} failed)` : ''),
      },
      {
        title: 'Scheduled',
        dataIndex: 'scheduledAt',
        key: 'scheduledAt',
        render: (v: string | null) => (v ? new Date(v).toLocaleString() : '—'),
      },
      {
        title: 'Created',
        dataIndex: 'createdAt',
        key: 'createdAt',
        render: (v: string) => new Date(v).toLocaleString(),
      },
      {
        title: 'Actions',
        key: 'actions',
        render: (_v, record) => (
          <Space>
            <Button size="small" onClick={() => setRecipientsForCampaign(record)}>
              Recipients
            </Button>
            {(record.status === 'DRAFT' || record.status === 'SCHEDULED') && (
              <Popconfirm
                title={`Launch ${record.name} to ${record.recipientCount} recipients?`}
                okText="Launch"
                onConfirm={() => onLaunch(record)}
              >
                <Button size="small" type="primary" loading={isLaunching}>
                  Launch
                </Button>
              </Popconfirm>
            )}
            {(record.status === 'DRAFT' || record.status === 'SCHEDULED') && (
              <Popconfirm
                title={`Cancel ${record.name}?`}
                okText="Cancel campaign"
                okButtonProps={{ danger: true }}
                onConfirm={() => onCancel(record)}
              >
                <Button size="small" loading={isCancelling}>
                  Cancel
                </Button>
              </Popconfirm>
            )}
            {(record.status === 'DRAFT' || record.status === 'CANCELLED') && (
              <Popconfirm
                title={`Delete ${record.name}?`}
                okText="Delete"
                okButtonProps={{ danger: true }}
                onConfirm={() => onDelete(record)}
              >
                <Button size="small" danger loading={isDeleting}>
                  Delete
                </Button>
              </Popconfirm>
            )}
          </Space>
        ),
      },
    ],
    [isCancelling, isDeleting, isLaunching],
  );

  const customerColumns: ColumnsType<Customer> = useMemo(
    () => [
      { title: 'Phone', dataIndex: 'phoneE164', key: 'phoneE164' },
      {
        title: 'Name',
        dataIndex: 'fullName',
        key: 'fullName',
        render: (v: string | null) => v ?? '—',
      },
      {
        title: 'Opt-in',
        dataIndex: 'optInStatus',
        key: 'optInStatus',
      },
    ],
    [],
  );

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>
          Campaigns
        </Title>
        <Button type="primary" onClick={openCreate}>
          New Campaign
        </Button>
      </Space>
      <Paragraph type="secondary">
        A campaign pairs a template with a recipient list. Hit{' '}
        <Text strong>Launch</Text> to fan out via RabbitMQ at 80 sends/sec per tenant.
        While a campaign is <Tag color="orange">SENDING</Tag>, this page refreshes every 2 seconds.
      </Paragraph>

      {error && (
        <Alert
          type="error"
          message="Failed to load campaigns"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Table<Campaign>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: (
            <TableEmpty
              icon={<RocketOutlined style={{ fontSize: 42, color: 'rgba(0,0,0,0.25)' }} />}
              title="No campaigns yet"
              hint="A campaign is a template + a list of customers. Create one to start blasting WhatsApp messages."
              actions={
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  Create campaign
                </Button>
              }
            />
          ),
        }}
        pagination={{
          current: (data?.number ?? 0) + 1,
          pageSize,
          total: data?.totalElements ?? 0,
          onChange: (p) => setPage(p - 1),
        }}
      />

      <Modal
        title="New Campaign"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={isCreating}
        okText="Create (DRAFT)"
        width={820}
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
        <Form<CreateFormValues> form={form} layout="vertical" onFinish={onCreate}>
          <Form.Item
            label="Campaign name"
            name="name"
            rules={[
              { required: true, message: 'Name is required' },
              { max: 200, message: 'Max 200 chars' },
            ]}
          >
            <Input placeholder="e.g. October promo blast" />
          </Form.Item>
          <Form.Item
            label="Template"
            name="templateId"
            rules={[{ required: true, message: 'Pick a template' }]}
          >
            <Select<string>
              placeholder={isLoadingCustomers ? 'Loading...' : 'Pick a Meta-approved template'}
              options={(templatesPage?.content ?? []).map((t: Template) => ({
                label: t.name,
                value: t.id,
              }))}
            />
          </Form.Item>
          <Form.Item label="Scheduled at (optional)" name="scheduledAt">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>

        <Title level={5} style={{ marginTop: 8 }}>
          Pick recipients
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 8 }}>
          <Text>{selectedCustomerIds.length}</Text> selected
        </Paragraph>
        <Table<Customer>
          rowKey="id"
          size="small"
          loading={isLoadingCustomers}
          columns={customerColumns}
          dataSource={customersPage?.content ?? []}
          scroll={{ x: 'max-content' }}
          pagination={{ pageSize: 10 }}
          rowSelection={{
            selectedRowKeys: selectedCustomerIds,
            onChange: (keys) => setSelectedCustomerIds(keys.map(String)),
          }}
        />
      </Modal>

      <Modal
        title={recipientsForCampaign ? `Recipients — ${recipientsForCampaign.name}` : 'Recipients'}
        open={recipientsForCampaign !== null}
        onCancel={() => setRecipientsForCampaign(null)}
        footer={null}
        width={780}
        destroyOnHidden
      >
        {recipientsForCampaign && <RecipientsPanel campaignId={recipientsForCampaign.id} />}
      </Modal>
    </div>
  );
}

function recipientStatusColor(s: string): string {
  switch (s) {
    case 'SENT':
      return 'blue';
    case 'DELIVERED':
      return 'cyan';
    case 'READ':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'PENDING':
    default:
      return 'default';
  }
}

function RecipientsPanel({ campaignId }: { campaignId: string }) {
  // Server-side pagination: lift page + pageSize into state so the user's choice
  // in the AntD page-size dropdown actually reaches the backend. Previously this
  // hardcoded `pageSize: 20` which AntD treats as a controlled (locked) value,
  // so the dropdown silently no-op'd. Also previously fetched size=200 in one go
  // which capped campaigns above 200 recipients — now the backend paginates.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const { data, isLoading } = useListRecipientsQuery(
    { id: campaignId, page, size: pageSize },
    { pollingInterval: 2000 },
  );
  const columns: ColumnsType<NonNullable<typeof data>['content'][number]> = [
    { title: 'Phone', dataIndex: 'customerPhone', key: 'customerPhone' },
    {
      title: 'Name',
      dataIndex: 'customerName',
      key: 'customerName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => (
        <Tag color={recipientStatusColor(s)} style={{ marginRight: 0 }}>
          {s}
        </Tag>
      ),
    },
    {
      title: 'Detail',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      render: (v: string | null) =>
        v ? (
          <Typography.Text type="danger" style={{ fontSize: 12 }} title={v}>
            {v.length > 70 ? `${v.slice(0, 70)}…` : v}
          </Typography.Text>
        ) : (
          '—'
        ),
    },
    {
      title: 'Sent at',
      dataIndex: 'sentAt',
      key: 'sentAt',
      render: (v: string | null) => (v ? new Date(v).toLocaleString() : '—'),
    },
  ];
  return (
    <Table
      rowKey="id"
      size="small"
      loading={isLoading}
      columns={columns}
      dataSource={data?.content ?? []}
      scroll={{ x: 'max-content' }}
      pagination={{
        current: page + 1,
        pageSize,
        total: data?.totalElements ?? 0,
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50', '100'],
        showTotal: (total, range) => `${range[0]}–${range[1]} of ${total}`,
        onChange: (newPage, newPageSize) => {
          // When pageSize changes AntD also calls onChange with the new page —
          // we reset to page 0 in that case so the user lands somewhere sensible
          // instead of an out-of-range page.
          if (newPageSize !== pageSize) {
            setPageSize(newPageSize);
            setPage(0);
          } else {
            setPage(newPage - 1);
          }
        },
      }}
    />
  );
}
