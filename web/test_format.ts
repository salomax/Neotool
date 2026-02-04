
import { formatMetricValue } from "./src/shared/utils/formatMetricValue";

console.log("--- Testing formatMetricValue ---");

// Test Case 1: Percentage with decimals: 2 (New behavior)
const val1 = 0.123456;
const res1 = formatMetricValue(val1, "percentage", { decimals: 2 });
console.log(`0.123456 as percentage (decimals: 2): ${res1.main}${res1.suffix}`);
if (res1.main !== "12,35") console.error("FAIL: Expected 12,35");

// Test Case 2: Percentage without decimals (Old behavior)
const val2 = 0.123456;
const res2 = formatMetricValue(val2, "percentage");
console.log(`0.123456 as percentage (default): ${res2.main}${res2.suffix}`);
// Previous behavior was max 1 digit. 12.3456 -> 12.3
if (res2.main !== "12,3") console.error("FAIL: Expected 12,3");

// Test Case 3: Percentage with decimals: 2, value is round
const val3 = 0.1;
const res3 = formatMetricValue(val3, "percentage", { decimals: 2 });
console.log(`0.1 as percentage (decimals: 2): ${res3.main}${res3.suffix}`);
if (res3.main !== "10,00") console.error("FAIL: Expected 10,00");

// Test Case 4: Percentage default, value is round
const val4 = 0.1;
const res4 = formatMetricValue(val4, "percentage");
console.log(`0.1 as percentage (default): ${res4.main}${res4.suffix}`);
// Previous behavior: max 1. 10 -> 10. (Actually toLocaleString default min is 0). So "10"
if (res4.main !== "10") console.error("FAIL: Expected 10");

console.log("--- End Test ---");
