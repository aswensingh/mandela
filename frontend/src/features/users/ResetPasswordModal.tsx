import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Radio,
  Space,
  Typography,
  message as antMessage,
} from 'antd';
import {
  useResetUserPasswordMutation,
  type ResetPasswordResponse,
} from './userApi';

const { Text, Paragraph } = Typography;

type Props = {
  open: boolean;
  userId: string | null;
  userEmail: string | null; // shown in the dialog so admin knows who they're resetting
  onClose: () => void;
};

/**
 * Two-step modal:
 *  1. Pick "Generate random" (default) or "Set custom password" → click Reset → server call
 *  2. On success, the new password is shown ONCE with a Copy button + warning.
 *     Closing the modal clears the password so it's not re-displayed if reopened.
 *
 * Used from both the Users page (tenant admin scope) and the Tenants page (platform admin
 * resets a tenant admin) — they target the same backend endpoint.
 */
export default function ResetPasswordModal({ open, userId, userEmail, onClose }: Props) {
  const [mode, setMode] = useState<'generate' | 'custom'>('generate');
  const [customPassword, setCustomPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ResetPasswordResponse | null>(null);
  const [reset, { isLoading }] = useResetUserPasswordMutation();

  // Reset internal state every time the dialog re-opens so old passwords don't leak.
  useEffect(() => {
    if (open) {
      setMode('generate');
      setCustomPassword('');
      setError(null);
      setResult(null);
    }
  }, [open, userId]);

  const submit = async () => {
    if (!userId) return;
    setError(null);
    const newPassword = mode === 'custom' ? customPassword : undefined;
    if (mode === 'custom' && customPassword.length < 8) {
      setError('Custom password must be at least 8 characters.');
      return;
    }
    try {
      const resp = await reset({ id: userId, newPassword }).unwrap();
      setResult(resp);
    } catch (err) {
      const apiMsg =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Failed to reset password';
      setError(apiMsg);
    }
  };

  const copyToClipboard = async () => {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.newPassword);
      antMessage.success('Password copied to clipboard');
    } catch {
      antMessage.warning("Couldn't access clipboard — copy it manually.");
    }
  };

  return (
    <Modal
      title={result ? 'Password reset' : 'Reset password'}
      open={open}
      onCancel={onClose}
      footer={
        result
          ? [
              <Button key="done" type="primary" onClick={onClose}>
                Done
              </Button>,
            ]
          : [
              <Button key="cancel" onClick={onClose}>
                Cancel
              </Button>,
              <Button key="reset" type="primary" danger loading={isLoading} onClick={submit}>
                Reset
              </Button>,
            ]
      }
      destroyOnHidden
    >
      {!result && (
        <>
          {userEmail && (
            <Paragraph>
              You are about to reset the password for <Text strong>{userEmail}</Text>. Any open
              sessions for this user will be terminated.
            </Paragraph>
          )}
          <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
            <Space direction="vertical">
              <Radio value="generate">Generate a random 16-character password</Radio>
              <Radio value="custom">Set a custom password</Radio>
            </Space>
          </Radio.Group>
          {mode === 'custom' && (
            <Input.Password
              style={{ marginTop: 12 }}
              placeholder="New password (min 8 characters)"
              value={customPassword}
              onChange={(e) => setCustomPassword(e.target.value)}
              autoFocus
            />
          )}
          {error && (
            <Alert type="error" message={error} style={{ marginTop: 12 }} showIcon />
          )}
        </>
      )}

      {result && (
        <>
          <Alert
            type="success"
            message={`Password ${result.generated ? 'generated' : 'set'} for ${result.email}.`}
            description="Copy it now — it will not be shown again. Convey it to the user out-of-band (in person, on a call, etc.) and ask them to change it on their next login (currently change-password isn't self-service, so they'd ask you to reset again)."
            showIcon
            style={{ marginBottom: 12 }}
          />
          <Space.Compact style={{ width: '100%' }}>
            <Input.Password
              value={result.newPassword}
              readOnly
              visibilityToggle={{ visible: true }}
            />
            <Button onClick={copyToClipboard}>Copy</Button>
          </Space.Compact>
        </>
      )}
    </Modal>
  );
}
