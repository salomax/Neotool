"use client";

import * as React from "react";
import useMediaQuery from "@mui/material/useMediaQuery";

interface UseScrollRevealOptions {
  rootMargin?: string;
  threshold?: number;
}

export function useScrollReveal<T extends HTMLElement>({
  rootMargin = "0px 0px -12% 0px",
  threshold = 0.2,
}: UseScrollRevealOptions = {}) {
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [node, setNode] = React.useState<T | null>(null);
  const [isVisible, setIsVisible] = React.useState(prefersReducedMotion);

  const targetRef = React.useCallback((element: T | null) => {
    setNode(element);
  }, []);

  React.useEffect(() => {
    if (prefersReducedMotion) {
      setIsVisible(true);
      return;
    }

    if (isVisible || typeof IntersectionObserver === "undefined") {
      return;
    }

    if (!node) {
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin, threshold },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [isVisible, node, prefersReducedMotion, rootMargin, threshold]);

  return {
    targetRef,
    isVisible,
  };
}
