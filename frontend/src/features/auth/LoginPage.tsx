import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Layout, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch } from '@/app/hooks';
import { credentialsReceived } from './authSlice';
import { useLoginMutation } from './authApi';

const { Content } = Layout;
const { Title } = Typography;

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
    <Layout style={{ minHeight: '100vh' }}>
      <Content
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
        }}
      >
        <Card style={{ width: 400 }}>
          <Title level={3} style={{ marginBottom: 24 }}>
            Sign in to MarketingHub
          </Title>
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
