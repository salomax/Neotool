"use client";

import React, { useState, useMemo } from "react";
import { Page, Container } from "@/shared/components/ui/layout";
import { Box, Typography, Grid, Stack, Link, useTheme, Tabs as MuiTabs, Tab } from "@/shared/ui/mui-imports";
import { useMediaQuery } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { SearchField } from "@/shared/components/ui/forms/SearchField";
import { useEconomicIndicators } from "@/features/financial-data/national-indicators/hooks/useEconomicIndicators";
import { useIndicatorsLatest, type IndicatorLatest } from "@/features/financial-data/national-indicators/hooks/useIndicatorsLatest";
import { IndicatorCard } from "@/features/financial-data/national-indicators/components/IndicatorCard";
import { CATEGORIES, CATEGORY_MAPPING } from "@/features/financial-data/national-indicators/config";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function NationalIndicatorsPage() {
    const { t } = useTranslation(financialDataTranslations);
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    
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

    // Enrich indicators with translations for search and display
    const enrichedIndicators = useMemo(() => {
        return indicators.map(indicator => ({
            ...indicator,
            translatedLabel: t(`nationalIndicators.indicators.${indicator.label}.label`, { defaultValue: indicator.label }),
            translatedDescription: t(`nationalIndicators.indicators.${indicator.label}.description`, { defaultValue: indicator.description || undefined })
        }));
    }, [indicators, t]);

    // Filter indicators
    const filteredIndicators = useMemo(() => {
        let result = enrichedIndicators;

        // Filter by search
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            result = result.filter(i => 
                i.translatedLabel.toLowerCase().includes(query) || 
                (i.translatedDescription && i.translatedDescription.toLowerCase().includes(query))
            );
        }

        // Filter by tab (category)
        if (selectedTab !== "all") {
             const categoryIndicators = CATEGORY_MAPPING[selectedTab] || [];
             result = result.filter(i => categoryIndicators.includes(i.label) || categoryIndicators.includes(i.id));
        }

        return result;
    }, [enrichedIndicators, searchQuery, selectedTab]);

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

    const sourceLinks = useMemo(() => ([
        {
            key: "sidra",
            label: t("nationalIndicators.dataSources.sources.sidra.label"),
            description: t("nationalIndicators.dataSources.sources.sidra.description"),
            href: "https://apisidra.ibge.gov.br/"
        },
        {
            key: "sgs",
            label: t("nationalIndicators.dataSources.sources.sgs.label"),
            description: t("nationalIndicators.dataSources.sources.sgs.description"),
            href: "https://dadosabertos.bcb.gov.br/dataset/22768-sgs"
        },
        {
            key: "comex",
            label: t("nationalIndicators.dataSources.sources.comex.label"),
            description: t("nationalIndicators.dataSources.sources.comex.description"),
            href: "https://www.gov.br/mdic/pt-br/assuntos/comercio-exterior/estatisticas/base-de-dados-bruta"
        },
        {
            key: "ipea",
            label: t("nationalIndicators.dataSources.sources.ipea.label"),
            description: t("nationalIndicators.dataSources.sources.ipea.description"),
            href: "https://www.ipea.gov.br/portal/categorias/2-uncategorised/1825-api"
        }
    ]), [t]);

    const openDataLinks = useMemo(() => ([
        {
            key: "ibge",
            label: t("nationalIndicators.dataSources.openData.ibge"),
            href: "https://www.ibge.gov.br/acesso-informacao/dados-abertos.html"
        },
        {
            key: "bcb",
            label: t("nationalIndicators.dataSources.openData.bcb"),
            href: "https://dadosabertos.bcb.gov.br/pages/perguntas-frequentes"
        },
        {
            key: "bcbLicense",
            label: t("nationalIndicators.dataSources.openData.bcbLicense"),
            href: "https://www.bcb.gov.br/acessoinformacao/dadosabertos"
        },
        {
            key: "mdic",
            label: t("nationalIndicators.dataSources.openData.mdic"),
            href: "https://www.gov.br/mdic/pt-br/assuntos/comercio-exterior/estatisticas/base-de-dados-bruta"
        },
        {
            key: "ipea",
            label: t("nationalIndicators.dataSources.openData.ipea"),
            href: "https://www.ipeadata.gov.br/"
        }
    ]), [t]);

    return (
        <Page>
             <Container maxWidth="xl" sx={{ px: { xs: 0, sm: 3 } }}>
                <Stack direction={isMobile ? "column" : "row"} sx={{ mb: 2 }}>
                    {!isMobile && (
                        <Box sx={{ width: 400 }}>
                            <SearchField 
                                value={searchQuery}
                                onChange={setSearchQuery}
                                placeholder={t("nationalIndicators.search.placeholder")}
                            />
                        </Box>
                    )}
                    <Box sx={{ 
                        width: isMobile ? '100%' : 'auto',
                        borderBottom: isMobile ? 1 : 0, 
                        borderColor: 'divider',
                        mx: 0, 
                        px: 0,
                        ml: !isMobile ? 2 : 0
                    }}>
                        <MuiTabs 
                            value={selectedTab}
                            onChange={(_, v) => setSelectedTab(v)}
                            variant="scrollable"
                            scrollButtons="auto"
                            allowScrollButtonsMobile
                            aria-label="Indicator tabs"
                        >
                            {tabItems.map((tab) => (
                                <Tab key={tab.id} label={tab.label} value={tab.id} />
                            ))}
                        </MuiTabs>
                    </Box>
                </Stack>
             </Container>

             <Container maxWidth="xl" sx={{ py: 4, px: { xs: 2, sm: 3 } }}>
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
                                        displayName={indicator.translatedLabel}
                                        displayDescription={indicator.translatedDescription}
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

            <Container maxWidth="xl" sx={{ pb: 6 }}>
                <Box sx={{ mt: 2, pt: 4, borderTop: 1, borderColor: "divider" }}>
                    <Typography variant="h6" fontWeight={600}>
                        {t("nationalIndicators.dataSources.title")}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1, maxWidth: 920 }}>
                        {t("nationalIndicators.dataSources.description")}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1, maxWidth: 920 }}>
                        {t("nationalIndicators.dataSources.methodology")}
                    </Typography>

                    <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
                        {t("nationalIndicators.dataSources.sourcesTitle")}
                    </Typography>
                    <Stack spacing={1}>
                        {sourceLinks.map(source => (
                            <Typography key={source.key} variant="body2" color="text.secondary">
                                <Link href={source.href} target="_blank" rel="noopener noreferrer" underline="hover">
                                    {source.label}
                                </Link>
                                {source.description ? ` — ${source.description}` : ""}
                            </Typography>
                        ))}
                    </Stack>

                    <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
                        {t("nationalIndicators.dataSources.openDataTitle")}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                        {t("nationalIndicators.dataSources.openDataDescription")}
                    </Typography>
                    <Stack spacing={1}>
                        {openDataLinks.map(link => (
                            <Typography key={link.key} variant="body2" color="text.secondary">
                                <Link href={link.href} target="_blank" rel="noopener noreferrer" underline="hover">
                                    {link.label}
                                </Link>
                            </Typography>
                        ))}
                    </Stack>

                    <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: "block" }}>
                        {t("nationalIndicators.dataSources.disclaimer")}
                    </Typography>
                </Box>
            </Container>
        </Page>
    );
}
