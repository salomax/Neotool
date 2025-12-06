"use client";

import React, { useState, useMemo } from "react";
import { Container, Box, PageTitle } from "@/shared/components/ui/layout";
import { useTranslation } from '@/shared/i18n';
import { authorizationManagementTranslations } from './i18n';
import Tabs from '@/shared/components/ui/navigation/Tabs';
import type { TabItem } from '@/shared/components/ui/navigation/Tabs';
import { UserManagement } from '@/shared/components/authorization/users/UserManagement';
import { GroupManagement } from '@/shared/components/authorization/groups/GroupManagement';
import { RoleManagement } from '@/shared/components/authorization/roles/RoleManagement';

export default function SettingsPage() {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [activeTab, setActiveTab] = useState<string>("users");

  const tabs: TabItem[] = useMemo(() => [
    {
      id: "users",
      label: t('tabs.users'),
      content: <UserManagement />,
    },
    {
      id: "groups",
      label: t('tabs.groups'),
      content: <GroupManagement />,
    },
    {
      id: "roles",
      label: t('tabs.roles'),
      content: <RoleManagement />,
    },
  ], [t]);

  const handleTabChange = (tabId: string) => {
    setActiveTab(tabId);
  };

  return (
    <Container  
      id="settings-page"
      fullHeight
      maxWidth="xl">
      <PageTitle>{t('title')}</PageTitle>
      <Box fullHeight>
        <Tabs
          tabs={tabs}
          value={activeTab}
          onChange={handleTabChange}
          variant="standard"
          indicatorColor="primary"
          textColor="primary"
        />
      </Box>
    </Container>
  );
}