import { CurrencyDisplay } from './web/src/shared/components/ui/data-display';

console.log('Testing CurrencyDisplay with color support');

// Test positive value (should be green)
console.log('Positive value:');
const positiveElement = CurrencyDisplay({ 
  value: 1000.50, 
  currency: 'BRL', 
  locale: 'pt-BR',
  showColor: true 
});
console.log('Should display green color for positive value');

// Test negative value (should be red)
console.log('Negative value:');
const negativeElement = CurrencyDisplay({ 
  value: -1000.50, 
  currency: 'BRL', 
  locale: 'pt-BR',
  showColor: true 
});
console.log('Should display red color for negative value');

// Test zero value (should be neutral)
console.log('Zero value:');
const zeroElement = CurrencyDisplay({ 
  value: 0, 
  currency: 'BRL', 
  locale: 'pt-BR',
  showColor: true 
});
console.log('Should display neutral color for zero value');

console.log('CurrencyDisplay component created successfully!');