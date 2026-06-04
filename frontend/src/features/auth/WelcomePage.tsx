import { Card, Typography } from 'antd';
import { useAppSelector } from '@/app/hooks';

const { Title, Paragraph } = Typography;

export default function WelcomePage() {
  const user = useAppSelector((state) => state.auth.user);
  return (
    <Card style={{ maxWidth: 640, margin: '0 auto' }}>
      <Title level={3}>Welcome, {user?.fullName ?? 'there'}</Title>
      <Paragraph>
        You are signed in as <strong>{user?.username}</strong>.
      </Paragraph>
      <Paragraph type="secondary" style={{ margin: 0 }}>
        Tenants, customers, campaigns, and conversations arrive in upcoming phases.
      </Paragraph>
    </Card>
  );
}
