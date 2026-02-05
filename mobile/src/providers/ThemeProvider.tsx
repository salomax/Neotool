import { ReactNode, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { PaperProvider } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { lightTheme, darkTheme } from '@/theme/theme';
import { ThemeContext } from '@/hooks/useTheme';

const THEME_KEY = '@app_theme';

interface ThemeProviderProps {
  children: ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const systemColorScheme = useColorScheme();
  const [isDark, setIsDark] = useState(systemColorScheme === 'dark');

  useEffect(() => {
    // Load saved theme preference
    AsyncStorage.getItem(THEME_KEY)
      .then((savedTheme) => {
        if (savedTheme !== null) {
          setIsDark(savedTheme === 'dark');
        }
      })
      .catch((error) => {
        console.error('Failed to load theme preference:', error);
      });
  }, []);

  const toggleTheme = () => {
    const newTheme = !isDark;
    setIsDark(newTheme);
    AsyncStorage.setItem(THEME_KEY, newTheme ? 'dark' : 'light').catch((error) => {
      console.error('Failed to save theme preference:', error);
    });
  };

  const theme = isDark ? darkTheme : lightTheme;

  return (
    <ThemeContext.Provider value={{ isDark, toggleTheme, theme, colors: theme.colors }}>
      <PaperProvider theme={theme}>{children}</PaperProvider>
    </ThemeContext.Provider>
  );
}
