import * as React from "react";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import ArrowDropUpRounded from "@mui/icons-material/ArrowDropUpRounded";
import ArrowDropDownRounded from "@mui/icons-material/ArrowDropDownRounded";

type Props = {
  title: string;
  value: string;
  delta?: { direction: "up" | "down"; label: string };
};

export function StatCard({ title, value, delta }: Props) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent>
        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          {title}
        </Typography>
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: 0.2 }}>
          {value}
        </Typography>
        {delta && (
          <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 0.5 }}>
            {delta.direction === "up" ? (
              <ArrowDropUpRounded color="success" fontSize="small" />
            ) : (
              <ArrowDropDownRounded color="error" fontSize="small" />
            )}
            <Typography variant="body2" color={delta.direction === "up" ? "success.main" : "error.main"}>
              {delta.label}
            </Typography>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}
