"use client";

import { useState, useEffect } from "react";

export function useResponsive() {
  const [isMobile, setIsMobile] = useState(false);
  const [isTablet, setIsTablet] = useState(false);
  const [isDesktop, setIsDesktop] = useState(false);

  useEffect(() => {
    const checkScreenSize = () => {
      if (typeof window === "undefined") return;
      
      const width = window.innerWidth;
      // Updated to match MUI md breakpoint (960px)
      setIsMobile(width < 960);
      setIsTablet(width >= 960 && width < 1280);
      setIsDesktop(width >= 1280);
    };

    checkScreenSize();
    window.addEventListener("resize", checkScreenSize);

    return () => window.removeEventListener("resize", checkScreenSize);
  }, []);

  return { isMobile, isTablet, isDesktop };
}
