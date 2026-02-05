module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      [
        'module-resolver',
        {
          root: ['./src'],
          alias: {
            '@': './src',
            '@/components': './src/components',
            '@/hooks': './src/hooks',
            '@/providers': './src/providers',
            '@/utils': './src/utils',
            '@/config': './src/config',
            '@/theme': './src/theme',
            '@/lib': './src/lib',
            '@/generated': './src/generated',
            '@/assets': './assets',
          },
          extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
        },
      ],
      'react-native-paper/babel',
      'react-native-reanimated/plugin', // Must be last
    ],
  };
};
