import * as React from 'react';
import { Divider, Box } from '@mui/material';
import { Label } from '@/shared/components/ui/primitives/Label';

interface UpdateFormSectionLabelProps {
  title: string;
}

export function UpdateFormSectionLabel({ title }: UpdateFormSectionLabelProps) {
  return (
    <Box
      sx={{
        pt: 0.5,
        mb: 1,
        display: 'flex',
        alignItems: 'flex-end',
        gap: 1,
      }}
    >
      <Label sx={{ flexShrink: 0, mb: 0 }}>{title}</Label>
      <Divider sx={{ flex: 1, mb: 0.5 }} />
    </Box>
  );
}
