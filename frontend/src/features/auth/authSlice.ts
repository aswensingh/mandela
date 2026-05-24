import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export type UserRole = 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'AGENT' | 'VIEWER';

export type AuthUser = {
  id: string;
  email: string;
  fullName: string;
  tenantId: string | null;
  role: UserRole;
};

export type AuthState = {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
};

export type Credentials = {
  user: AuthUser;
  accessToken: string;
  refreshToken: string;
};

const initialState: AuthState = {
  user: null,
  accessToken: null,
  refreshToken: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    credentialsReceived(state, action: PayloadAction<Credentials>) {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
    },
    loggedOut(state) {
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
    },
  },
});

export const { credentialsReceived, loggedOut } = authSlice.actions;
export default authSlice.reducer;
