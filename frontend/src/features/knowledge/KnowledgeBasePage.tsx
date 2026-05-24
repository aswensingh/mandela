import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  useDeleteDocumentMutation,
  useListDocumentsQuery,
  useUploadDocumentMutation,
  type KnowledgeDocument,
  type KnowledgeDocumentStatus,
} from './knowledgeApi';

const { Title, Paragraph } = Typography;
const { Dragger } = Upload;

const STATUS_COLOR: Record<KnowledgeDocumentStatus, string> = {
  PROCESSING: 'blue',
  PROCESSED: 'green',
  FAILED: 'red',
};

type UploadFormValues = {
  title: string;
};

export default function KnowledgeBasePage() {
  const [page, setPage] = useState(0);
  const pageSize = 50;
  const { data, isLoading, error } = useListDocumentsQuery(
    { page, size: pageSize },
    { pollingInterval: 4000, refetchOnMountOrArgChange: true },
  );
  const [uploadDoc, { isLoading: isUploading }] = useUploadDocumentMutation();
  const [deleteDoc, { isLoading: isDeleting }] = useDeleteDocumentMutation();

  const [modalOpen, setModalOpen] = useState(false);
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [form] = Form.useForm<UploadFormValues>();
  const [uploadError, setUploadError] = useState<string | null>(null);

  const reset = () => {
    setFiles([]);
    setUploadError(null);
    form.resetFields();
  };

  const onClose = () => {
    setModalOpen(false);
    reset();
  };

  const onUpload = async (values: UploadFormValues) => {
    setUploadError(null);
    if (files.length === 0 || !files[0].originFileObj) {
      setUploadError('Choose a file first');
      return;
    }
    const fd = new FormData();
    fd.append('file', files[0].originFileObj);
    if (values.title?.trim()) {
      fd.append('title', values.title.trim());
    }
    try {
      const created = await uploadDoc(fd).unwrap();
      message.success(`Uploaded "${created.title}" (${created.chunkCount} chunks)`);
      onClose();
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Upload failed';
      setUploadError(apiMessage);
    }
  };

  const onDelete = async (record: KnowledgeDocument) => {
    try {
      await deleteDoc(record.id).unwrap();
      message.success(`Deleted "${record.title}"`);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Delete failed';
      message.error(apiMessage);
    }
  };

  const columns: ColumnsType<KnowledgeDocument> = useMemo(
    () => [
      { title: 'Title', dataIndex: 'title', key: 'title' },
      { title: 'File', dataIndex: 'fileName', key: 'fileName' },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (s: KnowledgeDocumentStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
      },
      { title: 'Chunks', dataIndex: 'chunkCount', key: 'chunkCount' },
      {
        title: 'Uploaded',
        dataIndex: 'createdAt',
        key: 'createdAt',
        render: (v: string) => new Date(v).toLocaleString(),
      },
      {
        title: 'Actions',
        key: 'actions',
        render: (_v, record) => (
          <Popconfirm
            title={`Delete "${record.title}"?`}
            okText="Delete"
            okButtonProps={{ danger: true }}
            onConfirm={() => onDelete(record)}
          >
            <Button size="small" danger loading={isDeleting}>
              Delete
            </Button>
          </Popconfirm>
        ),
      },
    ],
    [isDeleting],
  );

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>
          Knowledge Base
        </Title>
        <Button type="primary" onClick={() => setModalOpen(true)}>
          Upload Document
        </Button>
      </Space>
      <Paragraph type="secondary">
        Upload PDFs, TXT or DOCX files. The bot's RAG advisor pulls relevant chunks
        when answering customer messages. Chunks are tenant-scoped — other tenants
        cannot see them.
      </Paragraph>

      {error && (
        <Alert
          type="error"
          message="Failed to load knowledge documents"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Table<KnowledgeDocument>
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
        title="Upload knowledge document"
        open={modalOpen}
        onCancel={onClose}
        onOk={() => form.submit()}
        okText="Upload"
        confirmLoading={isUploading}
        destroyOnHidden
      >
        {uploadError && (
          <Alert
            type="error"
            message={uploadError}
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Form<UploadFormValues> form={form} layout="vertical" onFinish={onUpload}>
          <Form.Item
            label="Title (optional)"
            name="title"
            extra="Defaults to the filename."
          >
            <Input placeholder="e.g. 2026 dental services price list" />
          </Form.Item>
          <Form.Item label="File">
            <Dragger
              multiple={false}
              accept=".pdf,.txt,.docx,.md,.html"
              beforeUpload={() => false}
              fileList={files}
              onChange={(info) => setFiles(info.fileList.slice(-1))}
              onRemove={() => setFiles([])}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">
                Click or drag a .pdf / .txt / .docx file
              </p>
            </Dragger>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
