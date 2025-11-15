
import { Text, View, Button } from "react-native";
import { useState } from "react";

export default function Home() {
  const [count, setCount] = useState(0);
  return (
    <View style={{ padding: 24 }}>
      <Text style={{ fontSize: 24, marginBottom: 8 }}>Invistus Mobile</Text>
      <Button title={`Count: ${count}`} onPress={() => setCount((c) => c + 1)} />
    </View>
  );
}
