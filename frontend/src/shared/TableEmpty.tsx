import { Empty, Space, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Text } = Typography;

/**
 * Reusable empty-state for AntD tables. Pass via `locale={{ emptyText: ... }}` on the Table.
 * Shown only when the page actually has zero rows AND the table isn't currently loading.
 * (AntD's Table swallows emptyText during loading automatically.)
 */
export default function TableEmpty({
  icon,
  title,
  hint,
  actions,
}: {
  icon?: ReactNode;
  title: string;
  hint?: string;
  actions?: ReactNode;
}) {
  return (
    <Empty
      image={icon ?? Empty.PRESENTED_IMAGE_SIMPLE}
      styles={{ image: { height: 60 } }}
      description={
        <Space direction="vertical" size={4} align="center" style={{ maxWidth: 360 }}>
          <Text strong>{title}</Text>
          {hint && (
            <Text type="secondary" style={{ fontSize: 13 }}>
              {hint}
            </Text>
          )}
        </Space>
      }
    >
      {actions && <div style={{ marginTop: 12 }}>{actions}</div>}
    </Empty>
  );
}
