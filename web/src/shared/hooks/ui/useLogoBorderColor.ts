"use client";

import * as React from "react";

function getAverageEdgeRgb(img: HTMLImageElement): string | null {
  const targetSize = 64;
  const canvas = document.createElement("canvas");
  canvas.width = targetSize;
  canvas.height = targetSize;

  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) return null;

  ctx.drawImage(img, 0, 0, targetSize, targetSize);

  const samples: Uint8ClampedArray[] = [];

  samples.push(ctx.getImageData(0, 0, targetSize, 1).data);
  samples.push(ctx.getImageData(0, targetSize - 1, targetSize, 1).data);
  samples.push(ctx.getImageData(0, 0, 1, targetSize).data);
  samples.push(ctx.getImageData(targetSize - 1, 0, 1, targetSize).data);

  let totalR = 0;
  let totalG = 0;
  let totalB = 0;
  let count = 0;

  for (const data of samples) {
    for (let i = 0; i < data.length; i += 4) {
      const a = data[i + 3] ?? 0;
      if (a < 32) continue;
      totalR += data[i] ?? 0;
      totalG += data[i + 1] ?? 0;
      totalB += data[i + 2] ?? 0;
      count += 1;
    }
  }

  if (count === 0) return null;
  const r = Math.round(totalR / count);
  const g = Math.round(totalG / count);
  const b = Math.round(totalB / count);
  return `rgb(${r}, ${g}, ${b})`;
}

/**
 * Samples edge pixels of a logo image and returns an RGB string for tinted UI (e.g. soft shadow).
 * Used by {@link FinancialAccountSelector} and investment asset identity.
 */
export function useLogoBorderColor(logoUrl?: string | null): string | null {
  const [borderColor, setBorderColor] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!logoUrl) {
      setBorderColor(null);
      return undefined;
    }

    let isActive = true;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.decoding = "async";

    img.onload = () => {
      try {
        const computed = getAverageEdgeRgb(img);
        if (!isActive) return;
        setBorderColor(computed);
      } catch {
        if (!isActive) return;
        setBorderColor(null);
      }
    };

    img.onerror = () => {
      if (!isActive) return;
      setBorderColor(null);
    };

    img.src = logoUrl;

    return () => {
      isActive = false;
    };
  }, [logoUrl]);

  return borderColor;
}
