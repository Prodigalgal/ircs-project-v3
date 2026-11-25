// File: frontends/admin/src/theme/ThemeModeContext.jsx
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { CssBaseline } from '@mui/material';
import { ThemeProvider } from '@mui/material/styles';
import {
    THEME_STORAGE_KEY,
    createAdminTheme,
    normalizeThemePreference,
    resolveThemeMode,
} from './themeConfig';
import { ThemeModeContext } from './themeModeStore';

function readStoredPreference() {
    if (typeof window === 'undefined') {
        return 'system';
    }
    try {
        return normalizeThemePreference(window.localStorage.getItem(THEME_STORAGE_KEY));
    } catch {
        return 'system';
    }
}

function readSystemPrefersDark() {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
        return false;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export function AppThemeProvider({ children }) {
    const [preference, setPreferenceState] = useState(readStoredPreference);
    const [systemPrefersDark, setSystemPrefersDark] = useState(readSystemPrefersDark);

    useEffect(() => {
        if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
            return undefined;
        }
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        const handleChange = (event) => {
            setSystemPrefersDark(event.matches);
        };
        mediaQuery.addEventListener('change', handleChange);
        return () => mediaQuery.removeEventListener('change', handleChange);
    }, []);

    const resolvedMode = useMemo(
        () => resolveThemeMode(preference, systemPrefersDark),
        [preference, systemPrefersDark],
    );

    const setPreference = useCallback((nextPreference) => {
        const normalized = normalizeThemePreference(nextPreference);
        setPreferenceState(normalized);
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, normalized);
        } catch {
            // Ignore storage restrictions in private browsing modes.
        }
    }, []);

    const toggleMode = useCallback(() => {
        setPreference(resolvedMode === 'dark' ? 'light' : 'dark');
    }, [resolvedMode, setPreference]);

    useEffect(() => {
        if (typeof document === 'undefined') {
            return;
        }
        document.documentElement.dataset.colorScheme = resolvedMode;
        document.documentElement.style.colorScheme = resolvedMode;
    }, [resolvedMode]);

    const theme = useMemo(() => createAdminTheme(resolvedMode), [resolvedMode]);

    const contextValue = useMemo(
        () => ({
            preference,
            resolvedMode,
            setPreference,
            toggleMode,
        }),
        [preference, resolvedMode, setPreference, toggleMode],
    );

    return (
        <ThemeModeContext.Provider value={contextValue}>
            <ThemeProvider theme={theme}>
                <CssBaseline />
                {children}
            </ThemeProvider>
        </ThemeModeContext.Provider>
    );
}

export default AppThemeProvider;
