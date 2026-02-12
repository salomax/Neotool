import { MD3LightTheme, MD3DarkTheme, MD3Theme } from 'react-native-paper';
import { lightColors, darkColors } from './colors';

export const lightTheme: MD3Theme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    ...lightColors,
  },
};

export const darkTheme: MD3Theme = {
  ...MD3DarkTheme,
  colors: {
    ...MD3DarkTheme.colors,
    ...darkColors,
  },
};

export type Theme = typeof lightTheme;
