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
import type { ColumnsType } from 'antd/es/table';
import {
  useCreateTemplateMutation,
  useDeleteTemplateMutation,
  useListTemplatesQuery,
  useUpdateTemplateMutation,
  type CreateTemplateRequest,
  type Template,
  type TemplateStatus,
} from './templateApi';

const { Title, Paragraph, Text } = Typography;

const STATUS_OPTIONS: TemplateStatus[] = [
  'APPROVED',
  'PENDING',
  'REJECTED',
  'PAUSED',
  'DISABLED',
];

const STATUS_COLOR: Record<TemplateStatus, string> = {
  APPROVED: 'green',
  PENDING: 'blue',
  REJECTED: 'red',
  PAUSED: 'orange',
  DISABLED: 'default',
};

type TemplateFormValues = {
  name: string;
  whatsappTemplateName: string;
  language: string;
  bodyPreview?: string;
  status?: TemplateStatus;
};

export default function TemplatesPage() {
  const [page, setPage] = useState(0);
  const pageSize = 50;
  const { data, isLoading, error } = useListTemplatesQuery({ page, size: pageSize });
  const [createTemplate, { isLoading: isCreating }] = useCreateTemplateMutation();
  const [updateTemplate, { isLoading: isUpdating }] = useUpdateTemplateMutation();
  const [deleteTemplate, { isLoading: isDeleting }] = useDeleteTemplateMutation();

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Template | null>(null);
  const [form] = Form.useForm<TemplateFormValues>();
  const [modalError, setModalError] = useState<string | null>(null);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'APPROVED' });
    setModalError(null);
    setModalOpen(true);
  };

  const openEdit = (record: Template) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      whatsappTemplateName: record.whatsappTemplateName,
      language: record.language,
      bodyPreview: record.bodyPreview ?? undefined,
      status: record.status,
    });
    setModalError(null);
    setModalOpen(true);
  };

  const onSubmit = async (values: TemplateFormValues) => {
    setModalError(null);
    try {
      if (editing) {
        await updateTemplate({
          id: editing.id,
          patch: {
            name: values.name,
            bodyPreview: values.bodyPreview ?? '',
            status: values.status,
          },
        }).unwrap();
        message.success('Template updated');
      } else {
        const payload: CreateTemplateRequest = {
          name: values.name,
          whatsappTemplateName: values.whatsappTemplateName,
          language: values.language,
          bodyPreview: values.bodyPreview || undefined,
          status: values.status,
        };
        await createTemplate(payload).unwrap();
        message.success('Template added');
      }
      setModalOpen(false);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        (editing ? 'Failed to update' : 'Failed to create');
      setModalError(apiMessage);
    }
  };

  const onDelete = async (record: Template) => {
    try {
      await deleteTemplate(record.id).unwrap();
      message.success(`Deleted ${record.whatsappTemplateName}`);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to delete';
      message.error(apiMessage);
    }
  };

  const columns: ColumnsType<Template> = useMemo(
    () => [
      { title: 'Name', dataIndex: 'name', key: 'name' },
      {
        title: 'Meta template',
        dataIndex: 'whatsappTemplateName',
        key: 'whatsappTemplateName',
        render: (v: string) => <Text code>{v}</Text>,
      },
      { title: 'Language', dataIndex: 'language', key: 'language' },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (s: TemplateStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
      },
      {
        title: 'Body preview',
        dataIndex: 'bodyPreview',
        key: 'bodyPreview',
        ellipsis: true,
        render: (v: string | null) => v ?? '—',
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
            <Button size="small" onClick={() => openEdit(record)}>
              Edit
            </Button>
            <Popconfirm
              title={`Delete ${record.whatsappTemplateName}?`}
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
          Message Templates
        </Title>
        <Button type="primary" onClick={openCreate}>
          Add Template
        </Button>
      </Space>

      <Paragraph type="secondary">
        Templates are approved on the Meta side. MarketingHub only stores their metadata so
        you can pick them when launching campaigns.
      </Paragraph>

      {error && (
        <Alert
          type="error"
          message="Failed to load templates"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Table<Template>
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
        title={editing ? `Edit ${editing.whatsappTemplateName}` : 'Add Template'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setModalError(null);
        }}
        onOk={() => form.submit()}
        confirmLoading={isCreating || isUpdating}
        okText={editing ? 'Save' : 'Add'}
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
        <Form<TemplateFormValues>
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          initialValues={{ status: 'APPROVED' }}
        >
          <Form.Item
            label="Internal label"
            name="name"
            rules={[
              { required: true, message: 'Name is required' },
              { max: 100, message: 'Max 100 chars' },
            ]}
          >
            <Input placeholder="e.g. October promo" />
          </Form.Item>
          <Form.Item
            label="Meta template name"
            name="whatsappTemplateName"
            rules={[
              { required: true, message: 'Required' },
              {
                pattern: /^[a-z0-9_]+$/,
                message: 'Lowercase letters, digits, underscores only',
              },
              { max: 100, message: 'Max 100 chars' },
            ]}
            extra="Must match the template name as approved on Meta."
          >
            <Input placeholder="october_promo_v2" disabled={!!editing} />
          </Form.Item>
          <Form.Item
            label="Language"
            name="language"
            rules={[
              { required: true, message: 'Required' },
              {
                pattern: /^[a-z]{2,3}(_[A-Z]{2})?$/,
                message: "Use 'en' or 'en_US' style codes",
              },
            ]}
          >
            <Input placeholder="en_US" disabled={!!editing} />
          </Form.Item>
          <Form.Item label="Body preview (optional)" name="bodyPreview">
            <Input.TextArea
              rows={3}
              placeholder="Hi {{1}}, our October promo runs through the 31st."
            />
          </Form.Item>
          <Form.Item label="Status" name="status">
            <Select options={STATUS_OPTIONS.map((s) => ({ label: s, value: s }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
