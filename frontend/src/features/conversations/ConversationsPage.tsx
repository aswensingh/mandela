import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Grid,
  Input,
  Layout,
  List,
  Popconfirm,
  Skeleton,
  Space,
  Switch,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message as antMessage,
} from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { setDebugInfoVisible } from '@/features/ui/uiSlice';
import type { UserRole } from '@/features/auth/authSlice';
import {
  useDeleteConversationMutation,
  useListConversationsQuery,
  useListMessagesQuery,
  useReleaseConversationMutation,
  useResetBotContextMutation,
  useSendAgentMessageMutation,
  useTakeOverConversationMutation,
  type ConversationFilter,
  type ConversationListItem,
  type ConversationMessage,
} from './conversationApi';

const { Title, Text, Paragraph } = Typography;
const { Sider, Content } = Layout;
const { TextArea } = Input;
const { useBreakpoint } = Grid;

const POLL_INTERVAL_MS = 5000;
const TAB_ITEMS: { key: ConversationFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'MINE', label: 'Mine' },
  { key: 'OPEN', label: 'Open' },
  { key: 'CLOSED', label: 'Closed' },
];

const REPLY_MAX = 4096;

function canControl(role: UserRole | undefined): boolean {
  return role === 'TENANT_ADMIN' || role === 'AGENT';
}

export default function ConversationsPage() {
  const [filter, setFilter] = useState<ConversationFilter>('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const role = useAppSelector((s) => s.auth.user?.role);
  const dispatch = useAppDispatch();
  const debugInfoVisible = useAppSelector((s) => s.ui.debugInfoVisible);
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  const { data, isLoading, error } = useListConversationsQuery(
    { filter, page: 0, size: 100 },
    { pollingInterval: POLL_INTERVAL_MS, refetchOnMountOrArgChange: true },
  );

  const conversations = data?.content ?? [];
  const selected = conversations.find((c) => c.id === selectedId) ?? null;

  // Mobile-only: when a conversation is open, hide the list and show only the thread.
  // Desktop / tablet keeps the original two-pane layout side by side.
  const showListOnMobile = isMobile && !selected;
  const showThreadOnMobile = isMobile && !!selected;

  // On mobile the top header + page title eat more relative space, so we lean on
  // dynamic viewport height (`dvh` is the modern fix for mobile-browser address bars).
  const paneHeight = isMobile ? 'calc(100dvh - 160px)' : 'calc(100vh - 220px)';

  const listCard = (
    <Card
      styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 } }}
      style={{ height: paneHeight, display: 'flex', flexDirection: 'column' }}
    >
      <Tabs
        activeKey={filter}
        onChange={(k) => setFilter(k as ConversationFilter)}
        items={TAB_ITEMS.map((t) => ({ key: t.key, label: t.label }))}
        style={{ padding: '0 12px' }}
      />
      <div style={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
        {isLoading ? (
          <Skeleton active style={{ padding: 16 }} />
        ) : conversations.length === 0 ? (
          <Empty description="No conversations" style={{ marginTop: 32 }} />
        ) : (
          <List
            dataSource={conversations}
            renderItem={(c) => (
              <ConversationRow
                conversation={c}
                selected={c.id === selectedId}
                onSelect={() => setSelectedId(c.id)}
              />
            )}
          />
        )}
      </div>
    </Card>
  );

  const threadCard = (
    <Card
      style={{ height: paneHeight, display: 'flex', flexDirection: 'column' }}
      styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 } }}
    >
      {!selected ? (
        <div
          style={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Empty description="Pick a conversation" />
        </div>
      ) : (
        <ConversationThread
          conversation={selected}
          role={role}
          isMobile={isMobile}
          onBack={() => setSelectedId(null)}
          onDeleted={() => setSelectedId(null)}
        />
      )}
    </Card>
  );

  return (
    <Layout style={{ background: 'transparent' }}>
      {/* On mobile, skip the page header when looking at a single thread — the
          back-button + customer name inside the thread already act as the header. */}
      {!showThreadOnMobile && (
        <>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 8,
            }}
          >
            <Title level={isMobile ? 4 : 3} style={{ margin: 0 }}>
              Conversations
            </Title>
            <Tooltip title="Show/hide AI confidence + auto-handoff reason. Turn OFF for demos.">
              <Space size={8}>
                <Switch
                  size="small"
                  checked={debugInfoVisible}
                  onChange={(v) => dispatch(setDebugInfoVisible(v))}
                />
                <Text type="secondary" style={{ fontSize: 13 }}>
                  AI debug info
                </Text>
              </Space>
            </Tooltip>
          </div>
          <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 16 }}>
            Inbound WhatsApp messages land here. List auto-refreshes every 5 seconds.
          </Paragraph>
        </>
      )}

      {error && (
        <Alert
          type="error"
          message="Failed to load conversations"
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      {isMobile ? (
        // Single-pane: list OR thread, never both.
        showListOnMobile ? listCard : threadCard
      ) : (
        <Layout style={{ background: 'transparent', gap: 16 }}>
          <Sider width={360} theme="light" style={{ background: 'transparent' }}>
            {listCard}
          </Sider>
          <Content>{threadCard}</Content>
        </Layout>
      )}
    </Layout>
  );
}

function ConversationRow({
  conversation,
  selected,
  onSelect,
}: {
  conversation: ConversationListItem;
  selected: boolean;
  onSelect: () => void;
}) {
  const title = conversation.customerName ?? conversation.customerPhone ?? conversation.customerId;
  const preview = conversation.lastMessageBody ?? '(no messages yet)';
  const when = conversation.lastMessageAt
    ? new Date(conversation.lastMessageAt).toLocaleString()
    : new Date(conversation.createdAt).toLocaleString();
  return (
    <List.Item
      onClick={onSelect}
      style={{
        cursor: 'pointer',
        padding: '12px 16px',
        background: selected ? 'rgba(22, 119, 255, 0.08)' : undefined,
        borderLeft: selected ? '3px solid #1677ff' : '3px solid transparent',
      }}
    >
      <List.Item.Meta
        title={
          <Space size={6}>
            <Text strong>{title}</Text>
            <StatusTag status={conversation.status} />
          </Space>
        }
        description={
          <div>
            <div
              style={{
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: 280,
              }}
            >
              {conversation.lastMessageDirection === 'OUT' && (
                <Text type="secondary">You: </Text>
              )}
              <Text type="secondary">{preview}</Text>
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {when}
            </Text>
          </div>
        }
      />
    </List.Item>
  );
}

function ConversationThread({
  conversation,
  role,
  isMobile,
  onBack,
  onDeleted,
}: {
  conversation: ConversationListItem;
  role: UserRole | undefined;
  isMobile: boolean;
  onBack: () => void;
  onDeleted: () => void;
}) {
  const debugInfoVisible = useAppSelector((s) => s.ui.debugInfoVisible);
  const { data, isLoading } = useListMessagesQuery(
    { id: conversation.id, page: 0, size: 200 },
    { pollingInterval: POLL_INTERVAL_MS },
  );

  const messages = useMemo(() => data?.content ?? [], [data]);

  const [takeOver, takeOverState] = useTakeOverConversationMutation();
  const [release, releaseState] = useReleaseConversationMutation();
  const [resetBotContext, resetState] = useResetBotContextMutation();
  const [deleteConversation, deleteState] = useDeleteConversationMutation();
  const [sendMessage, sendState] = useSendAgentMessageMutation();

  const [draft, setDraft] = useState('');
  // Clear the draft whenever the user switches conversation.
  useEffect(() => {
    setDraft('');
  }, [conversation.id]);

  // Auto-scroll the message list to the latest message.
  const scrollRef = useRef<HTMLDivElement | null>(null);
  // On open / switch (and once messages finish loading): jump straight to the bottom.
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [conversation.id, isLoading]);
  // On new messages (polling): follow them — but only if the user is already near the
  // bottom, so we don't yank them away while they're scrolled up reading history.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (nearBottom) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const controllable = canControl(role);
  const isClosed = conversation.status === 'CLOSED';
  const isHumanActive = conversation.status === 'HUMAN_ACTIVE';
  const isBotActive = conversation.status === 'BOT_ACTIVE';

  const onTakeOver = async () => {
    try {
      await takeOver(conversation.id).unwrap();
      antMessage.success('Conversation taken over — bot will stay silent.');
    } catch (err) {
      antMessage.error(extractError(err, 'Failed to take over conversation'));
    }
  };

  const onRelease = async () => {
    try {
      await release(conversation.id).unwrap();
      antMessage.success('Released back to bot.');
    } catch (err) {
      antMessage.error(extractError(err, 'Failed to release conversation'));
    }
  };

  const onResetBot = async () => {
    try {
      await resetBotContext(conversation.id).unwrap();
      antMessage.success("Bot context reset — it will start fresh on the next reply.");
    } catch (err) {
      antMessage.error(extractError(err, 'Failed to reset bot context'));
    }
  };

  const onDelete = async () => {
    try {
      await deleteConversation(conversation.id).unwrap();
      antMessage.success('Conversation deleted — chat history cleared.');
      onDeleted();
    } catch (err) {
      antMessage.error(extractError(err, 'Failed to delete conversation'));
    }
  };

  const onSend = async () => {
    const body = draft.trim();
    if (!body) return;
    try {
      await sendMessage({ id: conversation.id, body }).unwrap();
      setDraft('');
    } catch (err) {
      antMessage.error(extractError(err, 'Failed to send message'));
    }
  };

  // Send button enabled iff:
  //   - the user has a sending role (TENANT_ADMIN or AGENT)
  //   - the conversation is HUMAN_ACTIVE (server-side requirement)
  //   - there is some non-blank text to send
  const canSend = controllable && isHumanActive && draft.trim().length > 0 && !sendState.isLoading;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <div
        style={{
          borderBottom: '1px solid #f0f0f0',
          padding: isMobile ? 12 : 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <Space size={8} style={{ minWidth: 0, flex: 1 }}>
          {isMobile && (
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={onBack}
              aria-label="Back to conversation list"
            />
          )}
          <div style={{ minWidth: 0 }}>
            <Title
              level={5}
              style={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: isMobile ? 180 : 'none',
              }}
            >
              {conversation.customerName ?? conversation.customerPhone ?? conversation.customerId}
            </Title>
            <Space size={6}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {conversation.customerPhone}
              </Text>
              <StatusTag status={conversation.status} />
            </Space>
            {debugInfoVisible && handoffNote(conversation) && (
              <div>
                <Text style={{ fontSize: 11, color: '#d46b08' }}>
                  ⚠ {handoffNote(conversation)}
                </Text>
              </div>
            )}
          </div>
        </Space>
        <Space>
          {!isClosed && controllable && (
            <Popconfirm
              title="Reset bot context?"
              description={
                <div style={{ maxWidth: 280 }}>
                  The bot will forget everything said so far in this conversation and start
                  fresh on the next reply. The visible message history stays intact.
                </div>
              }
              okText="Reset"
              cancelText="Cancel"
              onConfirm={onResetBot}
            >
              <Tooltip title="Wipe the bot's memory for this conversation">
                <Button
                  icon={<ReloadOutlined />}
                  loading={resetState.isLoading}
                >
                  Reset Bot
                </Button>
              </Tooltip>
            </Popconfirm>
          )}
          {isBotActive && (
            <Tooltip
              title={
                controllable
                  ? 'Take over so the bot stops replying'
                  : 'Your role can view but not take over'
              }
            >
              <Button
                type="primary"
                onClick={onTakeOver}
                loading={takeOverState.isLoading}
                disabled={!controllable}
              >
                Take Over
              </Button>
            </Tooltip>
          )}
          {isHumanActive && (
            <Tooltip
              title={controllable ? 'Hand the conversation back to the bot' : 'Your role can view but not release'}
            >
              <Button
                onClick={onRelease}
                loading={releaseState.isLoading}
                disabled={!controllable}
              >
                Release to Bot
              </Button>
            </Tooltip>
          )}
          {isClosed && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Conversation is closed
            </Text>
          )}
          {controllable && (
            <Popconfirm
              title="Delete this conversation?"
              description={
                <div style={{ maxWidth: 300 }}>
                  Permanently deletes the entire chat history with this customer. The
                  customer record is kept, so you can re-test with the same number. This
                  cannot be undone.
                </div>
              }
              okText="Delete"
              okButtonProps={{ danger: true }}
              cancelText="Cancel"
              onConfirm={onDelete}
            >
              <Tooltip title="Delete the whole conversation (clean slate)">
                <Button danger icon={<DeleteOutlined />} loading={deleteState.isLoading}>
                  Delete
                </Button>
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      </div>
      <div
        ref={scrollRef}
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
          padding: 16,
          background: '#fafafa',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
      >
        {isLoading ? (
          <Skeleton active />
        ) : messages.length === 0 ? (
          <Empty description="No messages yet" style={{ marginTop: 32 }} />
        ) : (
          messages.map((m) => <MessageBubble key={m.id} message={m} />)
        )}
      </div>

      <div style={{ borderTop: '1px solid #f0f0f0', padding: 12 }}>
        {isClosed ? (
          <Text type="secondary">Conversation is closed — no further messages can be sent.</Text>
        ) : isBotActive ? (
          <Text type="secondary">
            Bot is handling this conversation. Take over to reply manually.
          </Text>
        ) : !controllable ? (
          <Text type="secondary">Your role can view this conversation but not reply.</Text>
        ) : (
          // AntD's `showCount` renders an absolutely-positioned counter that escapes any
          // flex container, so we render our own counter below the row instead. It only
          // appears once the user starts typing, keeping the composer clean when idle.
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <TextArea
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder="Type a reply…"
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  maxLength={REPLY_MAX}
                  disabled={sendState.isLoading}
                  onPressEnter={(e) => {
                    // Cmd/Ctrl+Enter sends; bare Enter inserts newline.
                    if (e.ctrlKey || e.metaKey) {
                      e.preventDefault();
                      void onSend();
                    }
                  }}
                />
              </div>
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={sendState.isLoading}
                disabled={!canSend}
                onClick={onSend}
              >
                Send
              </Button>
            </div>
            {draft.length > 0 && (
              <Text
                type="secondary"
                style={{
                  fontSize: 11,
                  textAlign: 'right',
                  color:
                    draft.length > REPLY_MAX * 0.95
                      ? '#cf1322'
                      : 'rgba(0,0,0,0.45)',
                }}
              >
                {draft.length.toLocaleString()} / {REPLY_MAX.toLocaleString()}
              </Text>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: ConversationMessage }) {
  const debugInfoVisible = useAppSelector((s) => s.ui.debugInfoVisible);
  const isOut = message.direction === 'OUT';
  const bubbleBg = isOut ? '#1677ff' : '#fff';
  const bubbleColor = isOut ? '#fff' : 'rgba(0,0,0,0.88)';
  const justify: 'flex-end' | 'flex-start' = isOut ? 'flex-end' : 'flex-start';
  const textAlign: 'right' | 'left' = isOut ? 'right' : 'left';
  const label = senderLabel(message);
  return (
    <div style={{ display: 'flex', justifyContent: justify }}>
      <div style={{ maxWidth: '70%' }}>
        <div
          style={{
            background: bubbleBg,
            color: bubbleColor,
            border: isOut ? 'none' : '1px solid #f0f0f0',
            borderRadius: 12,
            padding: '8px 12px',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {message.body}
        </div>
        <div style={{ marginTop: 4, fontSize: 11, color: 'rgba(0,0,0,0.45)', textAlign }}>
          {label} · {new Date(message.createdAt).toLocaleString()}
          {isOut && (
            <>
              {' · '}
              <Tag color={statusColor(message.status)} style={{ marginRight: 0 }}>
                {message.status}
              </Tag>
            </>
          )}
          {/* DEBUG: AI confidence per bot reply — gated by the "AI debug info" toggle. */}
          {debugInfoVisible && message.aiConfidence != null && (
            <>
              {' · '}
              <Tag color={confidenceColor(message.aiConfidence)} style={{ marginRight: 0 }}>
                conf {message.aiConfidence.toFixed(2)}
              </Tag>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function senderLabel(m: ConversationMessage): string {
  switch (m.senderType) {
    case 'CUSTOMER':
      return 'Customer';
    case 'AGENT':
      return 'Agent';
    case 'BOT':
      return 'Bot';
    case 'SYSTEM':
      return 'System';
    default:
      return m.senderType;
  }
}

function StatusTag({ status, style }: { status: ConversationListItem['status']; style?: CSSProperties }) {
  const { color, label } = (() => {
    switch (status) {
      case 'BOT_ACTIVE':
        return { color: 'blue', label: 'BOT' };
      case 'HUMAN_ACTIVE':
        return { color: 'gold', label: 'HUMAN' };
      case 'CLOSED':
      default:
        return { color: 'default', label: 'CLOSED' };
    }
  })();
  return (
    <Tag color={color} style={style}>
      {label}
    </Tag>
  );
}

// DEBUG helper: human-readable explanation of why a conversation is in HUMAN mode.
function handoffNote(c: ConversationListItem): string | null {
  if (c.status !== 'HUMAN_ACTIVE' || !c.handoffReason) return null;
  const conf =
    c.handoffConfidence != null ? ` · confidence ${c.handoffConfidence.toFixed(2)}` : '';
  switch (c.handoffReason) {
    case 'MODEL_REQUESTED':
      return `Auto handed-off: bot requested a human${conf}`;
    case 'LOW_CONFIDENCE':
      return `Auto handed-off: low confidence${conf}`;
    case 'MANUAL_TAKEOVER':
      return 'Taken over by agent';
    default:
      return `Handed off (${c.handoffReason})${conf}`;
  }
}

// DEBUG helper: colour the confidence tag green/gold/red. Remove with the tag itself.
function confidenceColor(c: number): string {
  if (c >= 0.8) return 'green';
  if (c >= 0.5) return 'gold';
  return 'red';
}

function statusColor(s: ConversationMessage['status']): string {
  switch (s) {
    case 'SENT':
      return 'blue';
    case 'DELIVERED':
      return 'cyan';
    case 'READ':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'QUEUED':
    default:
      return 'default';
  }
}

function extractError(err: unknown, fallback: string): string {
  // RTK Query surfaces fetch errors as { status, data } where data is our ApiError shape:
  // { error: { code, message, traceId } }
  if (err && typeof err === 'object' && 'data' in err) {
    const data = (err as { data: unknown }).data;
    if (data && typeof data === 'object' && 'error' in data) {
      const inner = (data as { error: unknown }).error;
      if (inner && typeof inner === 'object' && 'message' in inner) {
        const m = (inner as { message: unknown }).message;
        if (typeof m === 'string' && m.length > 0) return m;
      }
    }
  }
  return fallback;
}
