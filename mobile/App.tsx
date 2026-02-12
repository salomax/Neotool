import { SafeAreaView, StyleSheet, Text, View } from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        <Text style={styles.title}>Hello World</Text>
        <Text style={styles.subtitle}>Expo app is running on Android.</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  title: {
    fontSize: 36,
    fontWeight: '700',
    marginBottom: 8,
    color: '#111827',
  },
  subtitle: {
    fontSize: 16,
    color: '#4b5563',
  },
});
