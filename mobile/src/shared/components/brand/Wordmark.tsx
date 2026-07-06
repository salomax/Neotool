import { Image } from 'expo-image';
import { View, StyleSheet } from 'react-native';

export interface WordmarkProps {
  variant?: 'white' | 'blue';
  size?: 'small' | 'medium' | 'large' | 'xlarge';
  width?: number;
  height?: number;
}

const sizeMap = {
  small: { width: 173, height: 38 },
  medium: { width: 346, height: 76 },
  large: { width: 515, height: 113 },
  xlarge: { width: 647, height: 142 },
};

export function Wordmark({ variant = 'blue', size = 'medium', width, height }: WordmarkProps) {
  const dimensions = sizeMap[size];
  const logoWidth = width || dimensions.width;
  const logoHeight = height || dimensions.height;

  const source =
    variant === 'white'
      ? require('@/assets/logos/wordmark-white-transparent.svg')
      : require('@/assets/logos/wordmark-blue-transparent.svg');

  return (
    <View style={{ width: logoWidth, height: logoHeight }}>
      <Image source={source} style={styles.image} contentFit="contain" />
    </View>
  );
}

const styles = StyleSheet.create({
  image: {
    width: '100%',
    height: '100%',
  },
});
