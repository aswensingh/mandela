import { useMemo, useState } from 'react';
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
import { ContactsOutlined, ImportOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  useBulkDeleteCustomersMutation,
  useCreateCustomerMutation,
  useDeleteCustomerMutation,
  useListCustomersQuery,
  useUpdateCustomerMutation,
  type Customer,
  type CreateCustomerRequest,
  type OptInStatus,
} from './customerApi';
import ImportCsvModal from './ImportCsvModal';
import TableEmpty from '@/shared/TableEmpty';
import HelpLabel from '@/shared/HelpLabel';

const { Title } = Typography;

const OPT_IN_OPTIONS: OptInStatus[] = ['UNKNOWN', 'OPTED_IN', 'OPTED_OUT'];

const OPT_IN_COLORS: Record<OptInStatus, string> = {
  UNKNOWN: 'default',
  OPTED_IN: 'green',
  OPTED_OUT: 'red',
};

type CustomerFormValues = {
  phoneE164: string;
  fullName?: string;
  tagsCsv?: string;
  optInStatus?: OptInStatus;
};

const PAGE_SIZE_OPTIONS = [50, 100, 1000, 10000];

export default function CustomersPage() {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  // Fixed pixel height required by AntD's virtual table. Derived from the viewport so
  // the grid fills the available space; recomputed on every render (cheap) which covers
  // window resizes that trigger a re-render anyway.
  const tableScrollY = Math.max(360, window.innerHeight - 360);
  const [search, setSearch] = useState('');
  const [tag, setTag] = useState<string>('');
  const [optInStatus, setOptInStatus] = useState<OptInStatus | ''>('');

  const queryArgs = {
    page,
    size: pageSize,
    search: search || undefined,
    tag: tag || undefined,
    optInStatus: (optInStatus || undefined) as OptInStatus | undefined,
  };
  const { data, isLoading, error } = useListCustomersQuery(queryArgs);
  const [createCustomer, { isLoading: isCreating }] = useCreateCustomerMutation();
  const [updateCustomer, { isLoading: isUpdating }] = useUpdateCustomerMutation();
  const [deleteCustomer, { isLoading: isDeleting }] = useDeleteCustomerMutation();
  const [bulkDeleteCustomers, { isLoading: isBulkDeleting }] = useBulkDeleteCustomersMutation();

  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [form] = Form.useForm<CustomerFormValues>();
  const [modalError, setModalError] = useState<string | null>(null);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalError(null);
    setModalOpen(true);
  };

  const openEdit = (record: Customer) => {
    setEditing(record);
    form.setFieldsValue({
      phoneE164: record.phoneE164,
      fullName: record.fullName ?? undefined,
      tagsCsv: record.tags.join(', '),
      optInStatus: record.optInStatus,
    });
    setModalError(null);
    setModalOpen(true);
  };

  const onSubmit = async (values: CustomerFormValues) => {
    setModalError(null);
    const payload: CreateCustomerRequest = {
      phoneE164: values.phoneE164,
      fullName: values.fullName || undefined,
      tags: values.tagsCsv
        ? values.tagsCsv
            .split(',')
            .map((t) => t.trim())
            .filter(Boolean)
        : [],
      optInStatus: values.optInStatus,
    };
    try {
      if (editing) {
        await updateCustomer({ id: editing.id, patch: payload }).unwrap();
        message.success('Customer updated');
      } else {
        await createCustomer(payload).unwrap();
        message.success('Customer created');
      }
      setModalOpen(false);
    } catch (err) {
      setModalError(extractMessage(err, editing ? 'Failed to update' : 'Failed to create'));
    }
  };

  const onDelete = async (record: Customer) => {
    try {
      await deleteCustomer(record.id).unwrap();
      message.success(`Deleted ${record.phoneE164}`);
      setSelectedIds((prev) => prev.filter((id) => id !== record.id));
    } catch (err) {
      message.error(extractMessage(err, 'Failed to delete'));
    }
  };

  const onBulkDelete = async () => {
    if (selectedIds.length === 0) return;
    try {
      const result = await bulkDeleteCustomers(selectedIds).unwrap();
      message.success(`Deleted ${result.deleted} customer${result.deleted === 1 ? '' : 's'}`);
      setSelectedIds([]);
    } catch (err) {
      message.error(extractMessage(err, 'Failed to delete selected customers'));
    }
  };

  const columns: ColumnsType<Customer> = useMemo(
    () => [
      { title: 'Phone', dataIndex: 'phoneE164', key: 'phoneE164', width: 160 },
      {
        title: 'Full Name',
        dataIndex: 'fullName',
        key: 'fullName',
        width: 200,
        render: (val: string | null) => val ?? '—',
      },
      {
        title: 'Tags',
        dataIndex: 'tags',
        key: 'tags',
        width: 220,
        render: (tags: string[]) =>
          tags.length === 0 ? '—' : tags.map((t) => <Tag key={t}>{t}</Tag>),
      },
      {
        title: 'Opt-in',
        dataIndex: 'optInStatus',
        key: 'optInStatus',
        width: 120,
        render: (s: OptInStatus) => <Tag color={OPT_IN_COLORS[s]}>{s}</Tag>,
      },
      {
        title: 'Created',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 200,
        render: (val: string) => new Date(val).toLocaleString(),
      },
      {
        title: 'Actions',
        key: 'actions',
        width: 160,
        fixed: 'right',
        render: (_v, record) => (
          <Space>
            <Button size="small" onClick={() => openEdit(record)}>
              Edit
            </Button>
            <Popconfirm
              title={`Delete ${record.phoneE164}?`}
              okText="Delete"
              okButtonProps={{ danger: true }}
              onConfirm={() => onDelete(record)}
            >
              <Button size="small" danger loading={isDeleting}>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [isDeleting],
  );

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>
          Customers
        </Title>
        <Space>
          <Button onClick={() => setImportOpen(true)}>Import CSV</Button>
          <Button type="primary" onClick={openCreate}>
            Add Customer
          </Button>
        </Space>
      </Space>

      <Space style={{ marginBottom: 16, flexWrap: 'wrap' }}>
        <Input.Search
          placeholder="Search phone or name"
          allowClear
          onSearch={(value) => {
            setSearch(value);
            setPage(0);
          }}
          style={{ width: 260 }}
        />
        <Input
          placeholder="Tag filter"
          allowClear
          value={tag}
          onChange={(e) => {
            setTag(e.target.value);
            setPage(0);
          }}
          style={{ width: 180 }}
        />
        <Select<OptInStatus | ''>
          allowClear
          placeholder="Opt-in"
          style={{ width: 160 }}
          value={optInStatus || undefined}
          onChange={(v) => {
            setOptInStatus(v ?? '');
            setPage(0);
          }}
          options={OPT_IN_OPTIONS.map((s) => ({ label: s, value: s }))}
        />
      </Space>

      {error && (
        <Alert
          type="error"
          message="Failed to load customers"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      {selectedIds.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            <Space>
              <span>
                {selectedIds.length} selected
              </span>
              <Popconfirm
                title={`Delete ${selectedIds.length} customer${selectedIds.length === 1 ? '' : 's'}?`}
                description="This cannot be undone."
                okText="Delete"
                okButtonProps={{ danger: true }}
                onConfirm={onBulkDelete}
              >
                <Button danger size="small" loading={isBulkDeleting}>
                  Delete selected
                </Button>
              </Popconfirm>
              <Button size="small" onClick={() => setSelectedIds([])}>
                Clear
              </Button>
            </Space>
          }
        />
      )}

      <Table<Customer>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        // Virtual scrolling: only the visible rows are mounted in the DOM, so large
        // page sizes (1k–10k) stay responsive and "select all" no longer re-renders
        // thousands of rows. Requires a numeric scroll height + fixed column widths.
        virtual
        scroll={{ x: 1108, y: tableScrollY }}
        rowSelection={{
          selectedRowKeys: selectedIds,
          onChange: (keys) => setSelectedIds(keys as string[]),
          preserveSelectedRowKeys: true,
          // Virtual tables give any width-less column all the slack — pin the checkbox
          // column so the data columns scale proportionally instead of leaving a gap.
          columnWidth: 48,
        }}
        locale={{
          emptyText:
            // Only show the "Get started" CTA when there are no filters set — otherwise it's
            // misleading: customers might exist, they're just filtered out.
            !search && !tag && !optInStatus ? (
              <TableEmpty
                icon={<ContactsOutlined style={{ fontSize: 42, color: 'rgba(0,0,0,0.25)' }} />}
                title="No customers yet"
                hint="Bulk-upload a CSV (phone_e164, full_name, tags, opt_in_status) or add them one by one."
                actions={
                  <Space>
                    <Button type="primary" icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>
                      Import CSV
                    </Button>
                    <Button icon={<PlusOutlined />} onClick={openCreate}>
                      Add manually
                    </Button>
                  </Space>
                }
              />
            ) : undefined,
        }}
        pagination={{
          current: (data?.number ?? 0) + 1,
          pageSize,
          total: data?.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
          onChange: (p, size) => {
            // AntD fires onChange for both page and page-size changes. When the page-size
            // shrinks, the current page might no longer exist — jumping back to page 0
            // is the safe default.
            if (size !== pageSize) {
              setPageSize(size);
              setPage(0);
            } else {
              setPage(p - 1);
            }
          },
        }}
      />

      <Modal
        title={editing ? `Edit ${editing.phoneE164}` : 'Add Customer'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setModalError(null);
        }}
        onOk={() => form.submit()}
        confirmLoading={isCreating || isUpdating}
        okText={editing ? 'Save' : 'Create'}
        destroyOnHidden
      >
        {modalError && (
          <Alert
            type="error"
            message={modalError}
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Form<CustomerFormValues>
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          initialValues={{ optInStatus: 'UNKNOWN' }}
        >
          <Form.Item
            label={
              <HelpLabel
                text="Phone number"
                hint="International (E.164) format: a + sign, country code, then the local number, no spaces or dashes. Example: +12025550100. WhatsApp will not accept anything else."
              />
            }
            name="phoneE164"
            rules={[
              { required: true, message: 'Phone is required' },
              {
                pattern: /^\+[1-9]\d{1,14}$/,
                message: 'Must be E.164 format (+ then digits, max 15)',
              },
            ]}
          >
            <Input placeholder="+12025550100" />
          </Form.Item>
          <Form.Item label="Full name" name="fullName">
            <Input />
          </Form.Item>
          <Form.Item
            label={
              <HelpLabel
                text="Tags"
                hint="Free-form labels you can later filter campaigns by. Comma-separated. Examples: 'vip', 'monthly-newsletter', 'due-for-checkup'."
              />
            }
            name="tagsCsv"
          >
            <Input placeholder="vip, newsletter" />
          </Form.Item>
          <Form.Item
            label={
              <HelpLabel
                text="Opt-in status"
                hint="OPTED_IN = they've consented to receive promotional WhatsApp messages. Required by WhatsApp Business Platform policy before you can include them in a campaign. OPTED_OUT = they've explicitly declined. UNKNOWN = no signal either way."
              />
            }
            name="optInStatus"
          >
            <Select options={OPT_IN_OPTIONS.map((s) => ({ label: s, value: s }))} />
          </Form.Item>
        </Form>
      </Modal>

      <ImportCsvModal open={importOpen} onClose={() => setImportOpen(false)} />
    </div>
  );
}

function extractMessage(err: unknown, fallback: string): string {
  const apiMessage = (err as { data?: { error?: { message?: string } } })?.data?.error?.message;
  return apiMessage ?? fallback;
}
