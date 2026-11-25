// File: frontends/admin/src/theme/useThemeMode.js
import { useContext } from 'react';
import { ThemeModeContext } from './themeModeStore';

export function useThemeMode() {
    return useContext(ThemeModeContext);
}
