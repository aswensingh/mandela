import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import {
  useGetChatbotConfigQuery,
  useGetWhatsappConfigQuery,
  useSendTestMessageMutation,
  useSimulateInboundMutation,
  useTestWhatsappConnectionMutation,
  useUpdateChatbotConfigMutation,
  useUpdateWhatsappConfigMutation,
  type ConnectionTestResponse,
  type SendTestResponse,
} from './whatsappApi';
import HelpLabel from '@/shared/HelpLabel';

const { Title, Paragraph, Text } = Typography;

type FormValues = {
  phoneNumberId: string;
  accessToken: string;
  businessAccountId?: string;
};

type TestFormValues = {
  toE164: string;
  body: string;
};

export default function WhatsAppSettingsPage() {
  const { data, isLoading, error } = useGetWhatsappConfigQuery();
  const [update, { isLoading: isSaving }] = useUpdateWhatsappConfigMutation();
  const [sendTest, { isLoading: isSending }] = useSendTestMessageMutation();
  const [form] = Form.useForm<FormValues>();
  const [testForm] = Form.useForm<TestFormValues>();
  const [editing, setEditing] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [lastSend, setLastSend] = useState<SendTestResponse | null>(null);
  const [testConn, { isLoading: isTestingConn }] = useTestWhatsappConnectionMutation();
  const [connTestResult, setConnTestResult] = useState<ConnectionTestResponse | null>(null);

  const runConnectionTest = async () => {
    setConnTestResult(null);
    try {
      const res = await testConn().unwrap();
      setConnTestResult(res);
    } catch (err) {
      // The endpoint always returns 200 even on Meta-side failure, so this catch is
      // only for our-side network issues (network down, backend dead, etc.).
      const apiMsg =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ?? 'Test failed';
      setConnTestResult({
        ok: false,
        displayPhoneNumber: null,
        verifiedName: null,
        qualityRating: null,
        status: 0,
        error: apiMsg,
      });
    }
  };

  const onSubmit = async (values: FormValues) => {
    setSubmitError(null);
    try {
      await update(values).unwrap();
      message.success('WhatsApp credentials saved');
      form.resetFields();
      setEditing(false);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to save credentials';
      setSubmitError(apiMessage);
    }
  };

  const onSendTest = async (values: TestFormValues) => {
    setSendError(null);
    setLastSend(null);
    try {
      const result = await sendTest(values).unwrap();
      setLastSend(result);
      message.success('Sent to Meta');
      testForm.resetFields(['body']);
    } catch (err) {
      const apiMessage =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to send test message';
      setSendError(apiMessage);
    }
  };

  return (
    <div>
      <Title level={3} style={{ marginTop: 0 }}>
        WhatsApp Settings
      </Title>
      <Paragraph type="secondary">
        Paste your Meta Cloud API <Text code>phone_number_id</Text> and a permanent access
        token. The token is encrypted at rest — after saving we only show the last 4 characters.
      </Paragraph>

      {error && (
        <Alert
          type="error"
          message="Failed to load current configuration"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      <Card loading={isLoading} style={{ marginBottom: 24 }} title="Current status">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Configured">
            {data?.configured ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="Phone number ID">
            {data?.phoneNumberId ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Access token (last 4)">
            {data?.tokenLastFour ? <Text code>…{data.tokenLastFour}</Text> : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Business Account ID (WABA)">
            {data?.businessAccountId ? (
              <Text code>{data.businessAccountId}</Text>
            ) : (
              <Text type="secondary">— (needed for template status sync)</Text>
            )}
          </Descriptions.Item>
        </Descriptions>

        {/* Diagnostic: ping Meta with the stored creds and show what we get back.
            Useful for catching wrong tokens / expired tokens / wrong phone-number-id
            BEFORE attempting a real send. */}
        <div style={{ marginTop: 16 }}>
          <Button
            icon={<ThunderboltOutlined />}
            onClick={runConnectionTest}
            loading={isTestingConn}
            disabled={!data?.configured}
          >
            Test connection to Meta
          </Button>
          {!data?.configured && (
            <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
              Save credentials first
            </Text>
          )}
        </div>

        {connTestResult && (
          <Alert
            style={{ marginTop: 12 }}
            type={connTestResult.ok ? 'success' : 'error'}
            showIcon
            icon={connTestResult.ok ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
            message={
              connTestResult.ok
                ? `Connected — Meta sees this number as "${connTestResult.verifiedName ?? '(unverified)'}"`
                : `Connection failed (HTTP ${connTestResult.status ?? 0})`
            }
            description={
              connTestResult.ok ? (
                <Space direction="vertical" size={2} style={{ marginTop: 4 }}>
                  {connTestResult.displayPhoneNumber && (
                    <Text>
                      Display phone: <Text code>{connTestResult.displayPhoneNumber}</Text>
                    </Text>
                  )}
                  {connTestResult.qualityRating && (
                    <Text>
                      Quality rating:{' '}
                      <Tag color={qualityColor(connTestResult.qualityRating)}>
                        {connTestResult.qualityRating}
                      </Tag>
                    </Text>
                  )}
                  {connTestResult.error && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {connTestResult.error}
                    </Text>
                  )}
                </Space>
              ) : (
                connTestResult.error
              )
            }
            closable
            onClose={() => setConnTestResult(null)}
          />
        )}
      </Card>

      <Card title={editing || !data?.configured ? 'Set credentials' : 'Replace credentials'}>
        {!editing && data?.configured ? (
          <Space>
            <Button
              onClick={() => {
                // WABA ID isn't a secret, so carry it over; the token is never pre-filled.
                form.setFieldsValue({ businessAccountId: data?.businessAccountId ?? undefined });
                setEditing(true);
              }}
            >
              Replace credentials
            </Button>
          </Space>
        ) : (
          <>
            {submitError && (
              <Alert
                type="error"
                message={submitError}
                style={{ marginBottom: 12 }}
                showIcon
              />
            )}
            <Form<FormValues> form={form} layout="vertical" onFinish={onSubmit}>
              <Form.Item
                label={
                  <HelpLabel
                    text="Phone number ID"
                    hint="From Meta for Developers → your App → WhatsApp → API Setup. A 15-digit number like 123456789012345. NOT your phone number in E164 format."
                  />
                }
                name="phoneNumberId"
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder="e.g. 123456789012345" autoComplete="off" />
              </Form.Item>
              <Form.Item
                label={
                  <HelpLabel
                    text="Access token"
                    hint="Use a System User permanent token from Business Manager → Users → System Users. Do NOT use the 24-hour temporary token from the API Setup page — it expires."
                  />
                }
                name="accessToken"
                rules={[
                  { required: true, message: 'Required' },
                  { min: 8, message: 'Token looks too short' },
                ]}
                extra="Permanent token from Meta — kept encrypted and never echoed back."
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                label={
                  <HelpLabel
                    text="Business Account ID (WABA) — optional"
                    hint="Your WhatsApp Business Account ID from Meta for Developers → your App → WhatsApp → API Setup (labelled 'WhatsApp Business Account ID'). Only needed to sync template approval status from Meta — sending works without it."
                  />
                }
                name="businessAccountId"
                extra="Optional. Enables “Sync from Meta” on the Templates page."
              >
                <Input placeholder="e.g. 102290129340398" autoComplete="off" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Space>
                  <Button type="primary" htmlType="submit" loading={isSaving}>
                    Save
                  </Button>
                  {editing && (
                    <Button
                      onClick={() => {
                        form.resetFields();
                        setEditing(false);
                        setSubmitError(null);
                      }}
                    >
                      Cancel
                    </Button>
                  )}
                </Space>
              </Form.Item>
            </Form>
          </>
        )}
      </Card>

      {data?.testToolsEnabled && (
        <Card title="Send test message" style={{ marginTop: 24 }}>
        {!data?.configured && (
          <Alert
            type="info"
            message="Save WhatsApp credentials above before sending a test."
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        {sendError && (
          <Alert
            type="error"
            message={sendError}
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        {lastSend && (
          <Alert
            type={lastSend.status === 'SENT' ? 'success' : 'warning'}
            message={
              <span>
                <Tag color={lastSend.status === 'SENT' ? 'green' : 'orange'}>
                  {lastSend.status}
                </Tag>
                {lastSend.whatsappMessageId && (
                  <>
                    {' '}
                    wamid: <Text code>{lastSend.whatsappMessageId}</Text>
                  </>
                )}
              </span>
            }
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Form<TestFormValues>
          form={testForm}
          layout="vertical"
          onFinish={onSendTest}
          disabled={!data?.configured}
        >
          <Form.Item
            label="Recipient (E.164, e.g. +12025550100)"
            name="toE164"
            rules={[
              { required: true, message: 'Required' },
              {
                pattern: /^\+[1-9]\d{1,14}$/,
                message: 'Must be E.164 format (+ then digits)',
              },
            ]}
          >
            <Input placeholder="+12025550100" />
          </Form.Item>
          <Form.Item
            label="Message body"
            name="body"
            rules={[
              { required: true, message: 'Required' },
              { max: 4096, message: 'Max 4096 chars' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="Hello from MarketingHub!" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={isSending}>
              Send test
            </Button>
          </Form.Item>
        </Form>
      </Card>
      )}

      {data?.testToolsEnabled && <SimulateInboundCard />}
      <ChatbotSettingsCard />
    </div>
  );
}

type ChatbotFormValues = {
  aiSystemPrompt: string;
  handoffConfidenceThreshold: number;
};

function ChatbotSettingsCard() {
  const { data, isLoading, error } = useGetChatbotConfigQuery();
  const [update, { isLoading: isSaving }] = useUpdateChatbotConfigMutation();
  const [form] = Form.useForm<ChatbotFormValues>();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const onFinish = async (values: ChatbotFormValues) => {
    setSubmitError(null);
    try {
      await update({
        aiSystemPrompt: values.aiSystemPrompt || null,
        handoffConfidenceThreshold: values.handoffConfidenceThreshold,
      }).unwrap();
      message.success('Chatbot settings saved');
    } catch (err) {
      setSubmitError(
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
          'Failed to save chatbot settings',
      );
    }
  };

  return (
    <Card title="Chatbot" style={{ marginTop: 24 }} loading={isLoading}>
      <Paragraph type="secondary">
        Tune what the AI says to your customers. Leave the system prompt blank to fall back
        to the platform default. Confidence threshold is the minimum the model must report
        before the reply is sent — anything below hands off to a human.
      </Paragraph>
      {error && (
        <Alert
          type="error"
          message="Failed to load chatbot settings"
          style={{ marginBottom: 12 }}
          showIcon
        />
      )}
      {submitError && (
        <Alert
          type="error"
          message={submitError}
          style={{ marginBottom: 12 }}
          showIcon
        />
      )}
      <Form<ChatbotFormValues>
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          aiSystemPrompt: data?.aiSystemPrompt ?? '',
          handoffConfidenceThreshold: data?.handoffConfidenceThreshold ?? 0.5,
        }}
        key={JSON.stringify(data ?? {})}
      >
        <Form.Item
          label={
            <HelpLabel
              text="System prompt"
              hint="Sets the bot's persona and style. Example: 'You are a friendly receptionist at Acme Dental. Be polite and helpful, never invent appointment times, and direct billing questions to a human agent.' The bot uses this as its system message on every reply."
            />
          }
          name="aiSystemPrompt"
          rules={[{ max: 8000, message: 'Max 8000 chars' }]}
          extra="The persona / instructions for the bot. Blank uses the platform default."
        >
          <Input.TextArea rows={6} placeholder="You are a friendly support assistant..." />
        </Form.Item>
        <Form.Item
          label={
            <HelpLabel
              text="Handoff confidence threshold"
              hint="When the bot's own confidence in its answer drops below this value (0.0–1.0), the conversation auto-flips to HUMAN_ACTIVE and waits for an agent to take over. Lower = bot tries harder before handing off. 0.5 is a sensible default."
            />
          }
          name="handoffConfidenceThreshold"
          rules={[
            { required: true, message: 'Required' },
            { type: 'number', min: 0, max: 1, message: 'Between 0 and 1' },
          ]}
        >
          <InputNumber step={0.05} min={0} max={1} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Button type="primary" htmlType="submit" loading={isSaving}>
            Save chatbot settings
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
}

type SimulateFormValues = { fromE164: string; body: string };

/**
 * Pretend a customer just sent us a WhatsApp message. Goes through the exact same
 * webhook → conversation → AI reply pipeline as a real inbound, but bypasses Meta
 * entirely — useful because Meta's free test number has unreliable inbound webhook
 * delivery in development mode. Tenant admins get a guaranteed-working test loop.
 */
function SimulateInboundCard() {
  const [simulate, { isLoading }] = useSimulateInboundMutation();
  const [form] = Form.useForm<SimulateFormValues>();
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ wamid: string; accepted: number } | null>(null);

  const onFinish = async (values: SimulateFormValues) => {
    setError(null);
    setLastResult(null);
    try {
      const result = await simulate(values).unwrap();
      setLastResult(result);
      message.success('Inbound simulated — check Conversations');
    } catch (err) {
      const apiMsg =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Simulate failed';
      setError(apiMsg);
    }
  };

  return (
    <Card title="Simulate inbound message" style={{ marginTop: 24 }}>
      <Paragraph type="secondary">
        Trigger the full receive → AI reply → outbound loop with a fake customer message.
        Bypasses Meta's webhook delivery (which is flaky on the free test number) — useful
        for verifying your bot reply, knowledge-base answers, and agent take-over flow
        without burning real WhatsApp conversations.
      </Paragraph>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} showIcon />}
      {lastResult && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            lastResult.accepted > 0
              ? `Simulated message accepted (wamid: ${lastResult.wamid}).`
              : `Webhook processed but no message stored (accepted=0). Check the phone_number_id is set correctly.`
          }
          description={
            <Link to="/app/conversations">Open Conversations →</Link>
          }
        />
      )}
      <Form<SimulateFormValues>
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{ body: 'I need help with my booking' }}
      >
        <Form.Item
          label={
            <HelpLabel
              text="Sender phone (E.164)"
              hint="Any phone number you want the simulated customer to appear from. Doesn't have to be a real WhatsApp number — this is purely for routing inside MarketingHub."
            />
          }
          name="fromE164"
          rules={[
            { required: true, message: 'Required' },
            {
              pattern: /^\+[1-9]\d{1,14}$/,
              message: 'Must be E.164 format: + then digits',
            },
          ]}
        >
          <Input placeholder="+60172783758" autoComplete="off" />
        </Form.Item>
        <Form.Item
          label="Message body (what the customer 'sent')"
          name="body"
          rules={[
            { required: true, message: 'Required' },
            { max: 4096, message: 'Max 4096 chars' },
          ]}
        >
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item style={{ marginBottom: 0 }}>
          <Button type="primary" htmlType="submit" loading={isLoading}>
            Simulate inbound
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
}

/** Meta returns GREEN / YELLOW / RED for the WhatsApp Business quality rating. */
function qualityColor(rating: string): string {
  switch (rating.toUpperCase()) {
    case 'GREEN':
      return 'green';
    case 'YELLOW':
      return 'gold';
    case 'RED':
      return 'red';
    default:
      return 'default';
  }
}
