// File: frontends/admin/src/theme/themeModeStore.js
import { createContext } from 'react';

export const ThemeModeContext = createContext({
    preference: 'system',
    resolvedMode: 'dark',
    setPreference: () => {},
    toggleMode: () => {},
});
