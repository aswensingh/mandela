import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Layout, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch } from '@/app/hooks';
import { credentialsReceived } from './authSlice';
import { useLoginMutation } from './authApi';
import LoginParticles from './LoginParticles';

const { Content } = Layout;
const { Title, Text } = Typography;

type FormValues = { email: string; password: string };

export default function LoginPage() {
  const [login, { isLoading }] = useLoginMutation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const onFinish = async (values: FormValues) => {
    setErrorMsg(null);
    try {
      const result = await login(values).unwrap();
      dispatch(credentialsReceived(result));
      navigate('/app');
    } catch (err) {
      const message =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        'Login failed';
      setErrorMsg(message);
    }
  };

  return (
    // Deep-blue gradient gives the particles real contrast — light dots on grey were
    // washing into the background. The particle canvas now renders TRANSPARENT (set in
    // LoginParticles), so this gradient is what you actually see behind the dots.
    <Layout
      style={{
        minHeight: '100vh',
        background:
          'linear-gradient(135deg, #0a2540 0%, #0958d9 45%, #1677ff 100%)',
      }}
    >
      <LoginParticles />
      <Content
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
          // Sit above the particles canvas (which is z-index 0).
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Card
          style={{
            width: '100%',
            maxWidth: 400,
            // Slight translucency + frosted blur — keeps the card legible while still
            // letting the particle animation peek through the edges.
            background: 'rgba(255, 255, 255, 0.92)',
            backdropFilter: 'blur(8px)',
            WebkitBackdropFilter: 'blur(8px)',
            boxShadow: '0 10px 32px rgba(15, 23, 42, 0.12)',
          }}
        >
          <Title level={3} style={{ marginBottom: 8 }}>
            Sign in to MarketingHub
          </Title>
          <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
            Multi-tenant WhatsApp marketing platform.
          </Text>
          {errorMsg && (
            <Alert
              type="error"
              message={errorMsg}
              style={{ marginBottom: 16 }}
              showIcon
            />
          )}
          <Form layout="vertical" onFinish={onFinish} autoComplete="off">
            <Form.Item
              label="Email"
              name="email"
              rules={[
                { required: true, message: 'Email is required' },
                { type: 'email', message: 'Invalid email' },
              ]}
            >
              <Input autoComplete="email" />
            </Form.Item>
            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Password is required' }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={isLoading} block>
                Log in
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </Content>
    </Layout>
  );
}
