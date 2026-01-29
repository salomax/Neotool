import { useState, useEffect } from 'react';

/**
 * Hook to measure the height of a DOM element dynamically.
 * 
 * @param selector CSS selector for the element to measure
 * @param initialHeight Initial height value to avoid layout shift if known
 * @returns The current height of the element
 */
export function useElementHeight(selector: string, initialHeight: number = 0): number {
  const [height, setHeight] = useState(initialHeight);

  useEffect(() => {
    const updateHeight = () => {
      const element = document.querySelector(selector);
      if (element) {
        setHeight(element.getBoundingClientRect().height);
      }
    };

    // Initial measurement
    updateHeight();

    const element = document.querySelector(selector);
    if (!element) return;

    const observer = new ResizeObserver(updateHeight);
    observer.observe(element);

    return () => {
      observer.disconnect();
    };
  }, [selector]);

  return height;
}
