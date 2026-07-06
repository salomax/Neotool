import { ReactNode, useMemo, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { PaperProvider } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { lightTheme, darkTheme } from '@/shared/theme/theme';
import { ThemeContext } from '@/shared/hooks/useTheme';

const THEME_KEY = '@app_theme';

interface ThemeProviderProps {
  children: ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const systemColorScheme = useColorScheme();
  const [themePreference, setThemePreference] = useState<'light' | 'dark' | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(THEME_KEY)
      .then((savedTheme) => {
        if (savedTheme === 'light' || savedTheme === 'dark') {
          setThemePreference(savedTheme);
        }
      })
      .catch((error) => {
        console.error('Failed to load theme preference:', error);
      });
  }, []);

  const toggleTheme = () => {
    const currentIsDark = themePreference ? themePreference === 'dark' : systemColorScheme === 'dark';
    const nextPreference = currentIsDark ? 'light' : 'dark';

    setThemePreference(nextPreference);
    AsyncStorage.setItem(THEME_KEY, nextPreference).catch((error) => {
      console.error('Failed to save theme preference:', error);
    });
  };

  const isDark = themePreference ? themePreference === 'dark' : systemColorScheme === 'dark';
  const theme = isDark ? darkTheme : lightTheme;
  const contextValue = useMemo(
    () => ({ isDark, toggleTheme, theme, colors: theme.colors }),
    [isDark, theme]
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      <PaperProvider theme={theme}>{children}</PaperProvider>
    </ThemeContext.Provider>
  );
}
