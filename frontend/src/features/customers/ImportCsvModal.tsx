import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  useGetImportJobQuery,
  useStartImportMutation,
  type ImportJobStatus,
  type ImportRowError,
} from './importJobApi';

const { Paragraph, Text } = Typography;
const { Dragger } = Upload;

const STATUS_COLOR: Record<ImportJobStatus, string> = {
  PENDING: 'default',
  PROCESSING: 'blue',
  COMPLETED: 'green',
  FAILED: 'red',
};

type Props = {
  open: boolean;
  onClose: () => void;
};

export default function ImportCsvModal({ open, onClose }: Props) {
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [jobId, setJobId] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [startImport, { isLoading: isStarting }] = useStartImportMutation();

  const polling = useMemo(() => {
    if (!jobId) return 0;
    return 1000;
  }, [jobId]);

  const { data: job, refetch } = useGetImportJobQuery(jobId ?? '', {
    skip: !jobId,
    pollingInterval: polling,
  });

  useEffect(() => {
    if (job && (job.status === 'COMPLETED' || job.status === 'FAILED')) {
      // stop polling by clearing pollingInterval through a refetch tick
      refetch();
    }
  }, [job, refetch]);

  const reset = () => {
    setFiles([]);
    setJobId(null);
    setErrorMsg(null);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleUpload = async () => {
    setErrorMsg(null);
    if (files.length === 0 || !files[0].originFileObj) {
      setErrorMsg('Choose a CSV file first');
      return;
    }
    const form = new FormData();
    form.append('file', files[0].originFileObj);
    try {
      const j = await startImport(form).unwrap();
      setJobId(j.id);
      message.success('Upload accepted — processing…');
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to start import';
      setErrorMsg(apiMessage);
    }
  };

  const isFinished = job?.status === 'COMPLETED' || job?.status === 'FAILED';
  const percent = useMemo(() => {
    if (!job || job.totalRows === 0) return 0;
    return Math.round(((job.processedRows + job.failedRows) / job.totalRows) * 100);
  }, [job]);

  return (
    <Modal
      title="Import customers from CSV"
      open={open}
      onCancel={handleClose}
      width={720}
      footer={[
        jobId && isFinished ? (
          <Button key="done" type="primary" onClick={handleClose}>
            Done
          </Button>
        ) : !jobId ? (
          <Button
            key="upload"
            type="primary"
            loading={isStarting}
            disabled={files.length === 0}
            onClick={handleUpload}
          >
            Upload
          </Button>
        ) : null,
        <Button key="cancel" onClick={handleClose}>
          Close
        </Button>,
      ]}
    >
      {!jobId && (
        <>
          <Paragraph>
            CSV columns: <Text code>phone_e164,full_name,tags,opt_in_status</Text>. Header row
            required. Tags are semicolon-separated, e.g. <Text code>vip;promo</Text>.
            Existing customers (by tenant + phone) are <Text strong>updated in place</Text>.
          </Paragraph>
          {errorMsg && (
            <Alert type="error" message={errorMsg} style={{ marginBottom: 12 }} showIcon />
          )}
          <Dragger
            multiple={false}
            accept=".csv,text/csv"
            beforeUpload={() => false}
            fileList={files}
            onChange={(info) => setFiles(info.fileList.slice(-1))}
            onRemove={() => setFiles([])}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Click or drag a .csv file to upload</p>
          </Dragger>
        </>
      )}

      {job && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space>
            <Tag color={STATUS_COLOR[job.status]}>{job.status}</Tag>
            <Text type="secondary">{job.fileName}</Text>
          </Space>
          <Progress
            percent={percent}
            status={
              job.status === 'FAILED'
                ? 'exception'
                : job.status === 'COMPLETED'
                  ? 'success'
                  : 'active'
            }
          />
          <Space>
            <Text>Total: {job.totalRows}</Text>
            <Text type="success">Processed: {job.processedRows}</Text>
            <Text type="danger">Failed: {job.failedRows}</Text>
          </Space>
          {job.errorLog.length > 0 && (
            <Table<ImportRowError>
              size="small"
              rowKey={(r) => `${r.rowNumber}-${r.reason}`}
              dataSource={job.errorLog}
              pagination={{ pageSize: 10 }}
              columns={[
                { title: 'Row', dataIndex: 'rowNumber', width: 80 },
                { title: 'Reason', dataIndex: 'reason' },
                {
                  title: 'Raw line',
                  dataIndex: 'rawLine',
                  ellipsis: true,
                  render: (v: string | null) => v ?? '—',
                },
              ]}
            />
          )}
        </Space>
      )}
    </Modal>
  );
}
