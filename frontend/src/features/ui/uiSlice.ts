import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * Client-only UI state. Currently just the "AI debug info" toggle, which controls whether
 * developer/debug overlays (per-message AI confidence + auto-handoff reason) are shown in
 * the Conversations view. Persisted so it survives reloads — flip it OFF for demos.
 */
export type UiState = {
  debugInfoVisible: boolean;
};

const initialState: UiState = {
  debugInfoVisible: true,
};

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    setDebugInfoVisible(state, action: PayloadAction<boolean>) {
      state.debugInfoVisible = action.payload;
    },
  },
});

export const { setDebugInfoVisible } = uiSlice.actions;
export default uiSlice.reducer;
