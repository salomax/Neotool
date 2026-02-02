"use client";

import React, { useState, useMemo } from "react";
import { Page, Container } from "@/shared/components/ui/layout";
import { Box, Typography, Grid, Stack } from "@/shared/ui/mui-imports";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { SearchField } from "@/shared/components/ui/forms/SearchField";
import Tabs from "@/shared/components/ui/navigation/Tabs";
import { useEconomicIndicators } from "@/features/financial-data/national-indicators/hooks/useEconomicIndicators";
import { useIndicatorsLatest, type IndicatorLatest } from "@/features/financial-data/national-indicators/hooks/useIndicatorsLatest";
import { IndicatorCard } from "@/features/financial-data/national-indicators/components/IndicatorCard";
import { CATEGORIES, CATEGORY_MAPPING } from "@/features/financial-data/national-indicators/config";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function NationalIndicatorsPage() {
    const { t } = useTranslation(financialDataTranslations);
    usePageTitle(t("nationalIndicators.pageTitle"));
    useBreadcrumbLabel("national-indicators", t("nationalIndicators.pageTitle"));

    const { indicators, loading } = useEconomicIndicators();
    const { indicatorsLatest, loading: loadingLatest, error: errorLatest } = useIndicatorsLatest();
    const [searchQuery, setSearchQuery] = useState("");
    const [selectedTab, setSelectedTab] = useState("all");

    // Transform latest data for O(1) lookup: Map<IndicatorID, Map<VariationID, Data>>
    const latestDataMap = useMemo(() => {
        const map: Record<string, Record<string, IndicatorLatest>> = {};
        if (indicatorsLatest) {
            indicatorsLatest.forEach((item: IndicatorLatest) => {
                const indicatorKey = item.indicator.toUpperCase();
                if (!map[indicatorKey]) map[indicatorKey] = {};
                map[indicatorKey][item.variation] = item;
            });
        }
        return map;
    }, [indicatorsLatest]);

    // Filter indicators
    const filteredIndicators = useMemo(() => {
        let result = indicators;

        // Filter by search
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            result = result.filter(i => 
                i.label.toLowerCase().includes(query) || 
                (i.description && i.description.toLowerCase().includes(query))
            );
        }

        // Filter by tab (category)
        if (selectedTab !== "all") {
             const categoryIndicators = CATEGORY_MAPPING[selectedTab] || [];
             result = result.filter(i => categoryIndicators.includes(i.label) || categoryIndicators.includes(i.id));
        }

        return result;
    }, [indicators, searchQuery, selectedTab]);

    const sections = useMemo(() => {
        if (selectedTab !== "all") {
            return [{
                id: selectedTab,
                title: t(`nationalIndicators.tabs.${selectedTab}`),
                indicators: filteredIndicators
            }];
        }
        
        return CATEGORIES.map(cat => {
            const catIndicators = CATEGORY_MAPPING[cat] || [];
            const indicatorsInCat = filteredIndicators.filter(i => 
                catIndicators.includes(i.label) || catIndicators.includes(i.id)
            );
            return {
                id: cat,
                title: t(`nationalIndicators.tabs.${cat}`),
                indicators: indicatorsInCat
            };
        }).filter(s => s.indicators.length > 0);

    }, [selectedTab, filteredIndicators, t]);

    const tabItems = useMemo(() => [
        { id: "all", label: t("nationalIndicators.tabs.all"), content: null },
        ...CATEGORIES.map(c => ({
            id: c,
            label: t(`nationalIndicators.tabs.${c}`),
            content: null
        }))
    ], [t]);

    return (
        <Page>
             <Container maxWidth="xl">
                <Stack direction="row" sx={{ mb: 2 }}>
                    <Box sx={{ width: 400 }}>
                        <SearchField 
                            value={searchQuery}
                            onChange={setSearchQuery}
                            placeholder={t("nationalIndicators.search.placeholder")}
                        />
                    </Box>
                    <Tabs 
                        tabs={tabItems}
                        value={selectedTab}
                        onChange={setSelectedTab}
                        sx={{ width: 'auto' }}
                    />
                </Stack>
             </Container>

             <Container maxWidth="xl" sx={{ py: 4 }}>
                {loading && <Typography>{t("actions.loading")}</Typography>}
                
                {!loading && sections.length === 0 && (
                    <Typography>{t("messages.noResults")}</Typography>
                )}

                {sections.map(section => (
                    <Box key={section.id} sx={{ mb: 6 }}>
                        <Typography variant="overline" color="text.secondary" sx={{ mb: 2, display: "block" }}>
                            {section.title}
                        </Typography>
                        <Grid container spacing={3}>
                            {section.indicators.map(indicator => (
                                <Grid size={{ xs: 12, md: 6, lg: 4 }} key={indicator.id}>
                                    <IndicatorCard 
                                        indicator={indicator} 
                                        latestValues={
                                            latestDataMap[indicator.id] || 
                                            latestDataMap[indicator.id.toUpperCase()] || 
                                            latestDataMap[indicator.label] || 
                                            latestDataMap[indicator.label.toUpperCase()] || 
                                            {}
                                        }
                                        loading={loadingLatest}
                                    />
                                </Grid>
                            ))}
                        </Grid>
                    </Box>
                ))}
             </Container>
        </Page>
    );
}
