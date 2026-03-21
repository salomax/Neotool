"use client";

import React from "react";
import { Box } from "@mui/material";
import { CreditCard as CardIcon } from "@mui/icons-material";
import { CreditCardNetwork } from "@/lib/graphql/types/__generated__/graphql";

const CARD_NETWORK_SCALE: Partial<Record<CreditCardNetwork, number>> = {
  [CreditCardNetwork.Mastercard]: 0.8,
  [CreditCardNetwork.AmericanExpress]: 1.7,
  [CreditCardNetwork.DinersClub]: 1.1,
};

const CARD_NETWORK_LOGO: Partial<Record<CreditCardNetwork, string>> = {
  [CreditCardNetwork.Visa]: "/images/logos/card-networks/visa.svg",
  [CreditCardNetwork.Mastercard]: "/images/logos/card-networks/mastercard.svg",
  [CreditCardNetwork.AmericanExpress]: "/images/logos/card-networks/amex.svg",
  [CreditCardNetwork.Elo]: "/images/logos/card-networks/elo.svg",
  [CreditCardNetwork.Hipercard]: "/images/logos/card-networks/hipercard.svg",
  [CreditCardNetwork.DinersClub]: "/images/logos/card-networks/diners.svg",
};

interface CardNetworkIconProps {
  network?: CreditCardNetwork | string | null;
  /** Height in pixels. Defaults to 18. */
  size?: number;
  /** Fixed width in pixels shared by all logos. Defaults to size * 1.6. */
  width?: number;
  className?: string;
}

export function CardNetworkIcon({ network, size = 18, width, className }: CardNetworkIconProps) {
  const key = network as CreditCardNetwork;
  const logoUrl = network ? CARD_NETWORK_LOGO[key] : undefined;
  const fixedWidth = width ?? Math.round(size * 1.6);
  const scale = (network ? CARD_NETWORK_SCALE[key] : undefined) ?? 1;

  if (logoUrl) {
    return (
      <Box
        component="img"
        src={logoUrl}
        alt={network ?? ""}
        className={className}
        sx={{ height: size, width: fixedWidth, objectFit: "contain", display: "block", flexShrink: 0, transform: scale !== 1 ? `scale(${scale})` : undefined }}
      />
    );
  }

  return (
    <Box sx={{ width: fixedWidth, height: size, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
      <CardIcon sx={{ fontSize: size, color: "text.secondary" }} className={className} />
    </Box>
  );
}
