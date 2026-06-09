import { useMemo, useState } from 'react';
import {
  Alert,
  AutoComplete,
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
import { FileTextOutlined, PlusOutlined, SyncOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  useCreateTemplateMutation,
  useDeleteTemplateMutation,
  useListTemplatesQuery,
  useSyncTemplatesMutation,
  useUpdateTemplateMutation,
  type CreateTemplateRequest,
  type Template,
  type TemplateStatus,
} from './templateApi';
import TableEmpty from '@/shared/TableEmpty';
import HelpLabel from '@/shared/HelpLabel';

const { Title, Paragraph, Text } = Typography;

const STATUS_OPTIONS: TemplateStatus[] = [
  'APPROVED',
  'PENDING',
  'REJECTED',
  'PAUSED',
  'DISABLED',
];

// Common WhatsApp template language codes. This is a suggestion list for an AutoComplete,
// NOT an exhaustive allow-list — Meta supports ~80 codes and you can type any valid one
// (format 'en' or 'en_US'). The important part: Meta usually registers English templates
// (incl. its 'hello_world' sample) under en_US, not en — so both are offered.
const LANGUAGE_OPTIONS = [
  { label: 'en_US — English (US)', value: 'en_US' },
  { label: 'en_GB — English (UK)', value: 'en_GB' },
  { label: 'en — English', value: 'en' },
  { label: 'ms — Malay', value: 'ms' },
  { label: 'id — Indonesian', value: 'id' },
  { label: 'zh_CN — Chinese (Simplified)', value: 'zh_CN' },
  { label: 'zh_HK — Chinese (Hong Kong)', value: 'zh_HK' },
  { label: 'zh_TW — Chinese (Traditional)', value: 'zh_TW' },
  { label: 'ta — Tamil', value: 'ta' },
  { label: 'th — Thai', value: 'th' },
  { label: 'vi — Vietnamese', value: 'vi' },
];

const LANGUAGE_RULES = [
  { required: true, message: 'Required' },
  {
    pattern: /^[a-z]{2,3}(_[A-Z]{2})?$/,
    message: "Use a Meta code like 'en' or 'en_US'",
  },
];

const STATUS_COLOR: Record<TemplateStatus, string> = {
  APPROVED: 'green',
  PENDING: 'blue',
  REJECTED: 'red',
  PAUSED: 'orange',
  DISABLED: 'default',
  NOT_FOUND: 'volcano',
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
  const [syncTemplates, { isLoading: isSyncing }] = useSyncTemplatesMutation();

  const onSync = async () => {
    try {
      const result = await syncTemplates().unwrap();
      message.success(result.message);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to sync from Meta';
      message.error(apiMessage);
    }
  };

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Template | null>(null);
  const [form] = Form.useForm<TemplateFormValues>();
  const [modalError, setModalError] = useState<string | null>(null);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'PENDING' });
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
            language: values.language,
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
        width: 340,
        // Clamp the truncation in the cell's own markup. The column's `ellipsis`/`width`
        // alone don't work under scroll:max-content (the table auto-sizes to content), but a
        // fixed-width div with overflow:hidden can't widen the column, so it truncates to "…".
        // Hover (title) shows the full text.
        render: (v: string | null) =>
          v ? (
            <div
              title={v}
              style={{
                maxWidth: 320,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {v}
            </div>
          ) : (
            '—'
          ),
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
        <Space>
          <Button icon={<SyncOutlined />} onClick={onSync} loading={isSyncing}>
            Sync from Meta
          </Button>
          <Button type="primary" onClick={openCreate}>
            Add Template
          </Button>
        </Space>
      </Space>

      <Paragraph type="secondary">
        Templates are approved on the Meta side. MarketingHub only stores their metadata so
        you can pick them when launching campaigns. Hit <Text strong>Sync from Meta</Text> to pull
        each template's real approval status (needs your WhatsApp Business Account ID in WhatsApp
        Settings). A <Tag color="volcano">NOT_FOUND</Tag> status means Meta has no template with
        that exact name + language.
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
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: (
            <TableEmpty
              icon={<FileTextOutlined style={{ fontSize: 42, color: 'rgba(0,0,0,0.25)' }} />}
              title="No templates yet"
              hint="Templates are reusable message bodies with {name}-style placeholders. Create one to use it in a campaign."
              actions={
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  Create template
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
          initialValues={{ status: 'PENDING' }}
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
            label={
              <HelpLabel
                text="Meta template name"
                hint="This must match exactly the template name as approved by Meta in your WhatsApp Manager. Only lowercase letters, digits, and underscores. Example: 'october_promo_v2'. WhatsApp rejects messages where this doesn't match."
              />
            }
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
            label={
              <HelpLabel
                text="Language"
                hint="Must match the language your template is approved under in Meta — exactly. Meta treats 'en' and 'en_US' as different languages, and registers most English templates (including its 'hello_world' sample) as en_US. Pick a suggestion or type any valid code."
              />
            }
            name="language"
            rules={LANGUAGE_RULES}
            extra="Type to search, or enter any Meta code (e.g. en_US). Must match Meta exactly."
          >
            <AutoComplete
              options={LANGUAGE_OPTIONS}
              placeholder="e.g. en_US"
              filterOption={(input, option) =>
                (option?.value ?? '').toLowerCase().includes(input.toLowerCase()) ||
                (String(option?.label) ?? '').toLowerCase().includes(input.toLowerCase())
              }
            />
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
