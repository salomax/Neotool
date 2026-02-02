import { Box, Divider, Typography } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";

export function BacenDataFooter() {
  const { t } = useTranslation(financialDataTranslations);

  return (
    <Box sx={{ mt: 8, mb: 2 }}>
      <Divider sx={{ mb: 2 }} />
      <Typography variant="caption" color="text.secondary" display="block">
        {t("footer.ifDataSource")}
      </Typography>
      <Typography variant="caption" color="text.secondary" display="block">
        {t("footer.ratingSource")}
      </Typography>
    </Box>
  );
}
