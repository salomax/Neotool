import { createContext, useContext } from 'react';
import { Theme } from '@/theme/theme';

interface ThemeContextType {
  isDark: boolean;
  toggleTheme: () => void;
  theme: Theme;
  colors: Theme['colors'];
}

export const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}
