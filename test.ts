import { formatLargeCurrency } from './web/src/shared/components/ui/forms/components/masks/br';

console.log('Testing formatLargeCurrency');

// Test case 1: Large positive number
const positiveValue = 30364037.79;
const formattedPositive = formatLargeCurrency(positiveValue);
console.log(`Input: ${positiveValue}, Output: ${formattedPositive}`);

// Test case 2: Large negative number
const negativeValue = -30364037.79;
const formattedNegative = formatLargeCurrency(negativeValue);
console.log(`Input: ${negativeValue}, Output: ${formattedNegative}`);

// Test case 3: Positive number in billions
const positiveBillion = 30000000000;
const formattedPositiveBillion = formatLargeCurrency(positiveBillion);
console.log(`Input: ${positiveBillion}, Output: ${formattedPositiveBillion}`);

// Test case 4: Negative number in billions
const negativeBillion = -30000000000;
const formattedNegativeBillion = formatLargeCurrency(negativeBillion);
console.log(`Input: ${negativeBillion}, Output: ${formattedNegativeBillion}`);

// Test case 5: Zero
const zeroValue = 0;
const formattedZero = formatLargeCurrency(zeroValue);
console.log(`Input: ${zeroValue}, Output: ${formattedZero}`);
