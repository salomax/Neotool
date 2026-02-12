import { Image } from 'expo-image';
import { View, StyleSheet, DimensionValue } from 'react-native';

export interface LogoProps {
  variant?: 'white' | 'blue';
  size?: 'small' | 'medium' | 'large' | 'xlarge';
  width?: number;
  height?: number;
}

const sizeMap = {
  small: { width: 40, height: 38 },
  medium: { width: 80, height: 76 },
  large: { width: 120, height: 113 },
  xlarge: { width: 150, height: 142 },
};

export function Logo({ 
  variant = 'blue', 
  size = 'medium',
  width,
  height,
}: LogoProps) {
  const dimensions = sizeMap[size];
  const logoWidth = width || dimensions.width;
  const logoHeight = height || dimensions.height;

  const source = variant === 'white' 
    ? require('@/assets/logos/invistus-logo-white-transparent.svg')
    : require('@/assets/logos/invistus-logo-blue-transparent.svg');

  return (
    <View style={{ width: logoWidth, height: logoHeight }}>
      <Image
        source={source}
        style={styles.image}
        contentFit="contain"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  image: {
    width: '100%',
    height: '100%',
  },
});
