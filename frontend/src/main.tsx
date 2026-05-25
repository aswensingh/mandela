// React-19 compatibility patch for AntD 5's static-method usage
// (message.success, Modal.confirm, notification.*, etc.). Must be imported
// BEFORE any antd component is touched.
import '@ant-design/v5-patch-for-react-19';

import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp, ConfigProvider } from 'antd';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { persistor, store } from './app/store';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <PersistGate loading={null} persistor={persistor}>
        <BrowserRouter>
          <ConfigProvider>
            {/* AntdApp provides context so message/modal/notification respect
                theme tokens and live inside the React tree even when called
                via the static helpers (message.success, etc.). */}
            <AntdApp>
              <App />
            </AntdApp>
          </ConfigProvider>
        </BrowserRouter>
      </PersistGate>
    </Provider>
  </React.StrictMode>,
);
