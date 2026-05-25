import { QuestionCircleOutlined } from '@ant-design/icons';
import { Space, Tooltip } from 'antd';
import type { ReactNode } from 'react';

/**
 * Renders an AntD `Form.Item` label with a hover-(?) tooltip explaining the field.
 *
 *     <Form.Item label={<HelpLabel text="phone_number_id" hint="..." />} ... />
 *
 * Keeps Forms readable without dropping into a full <Form.Item help={...}> banner.
 */
export default function HelpLabel({
  text,
  hint,
}: {
  text: ReactNode;
  hint: ReactNode;
}) {
  return (
    <Space size={4}>
      {text}
      <Tooltip title={hint} placement="topLeft">
        <QuestionCircleOutlined style={{ color: 'rgba(0,0,0,0.45)', cursor: 'help' }} />
      </Tooltip>
    </Space>
  );
}
