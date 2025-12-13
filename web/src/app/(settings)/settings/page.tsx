"use client";

import React, { useState, useMemo } from "react";
import { Container, Box } from "@/shared/components/ui/layout";
import PersonIcon from "@mui/icons-material/Person";
import GroupIcon from "@mui/icons-material/Group";
import { ShieldCheckIcon } from "@/shared/components/ui/icons/ShieldCheckIcon";
import { useTranslation } from '@/shared/i18n';
import { authorizationManagementTranslations } from './i18n';
import Tabs from '@/shared/components/ui/navigation/Tabs';
import type { TabItem } from '@/shared/components/ui/navigation/Tabs';
import { UserManagement } from '../authorization/users/UserManagement';
import { GroupManagement } from '../authorization/groups/GroupManagement';
import { RoleManagement } from '../authorization/roles/RoleManagement';
import { usePageTitle } from '@/shared/hooks/ui';

export default function SettingsPage() {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [activeTab, setActiveTab] = useState<string>("users");

  // Set page title in global header
  usePageTitle(t('title'));

  const tabs: TabItem[] = useMemo(() => [
    {
      id: "users",
      label: t('tabs.users'),
      icon: <PersonIcon sx={{ fontSize: '1.25rem' }} />,
      content: <UserManagement />,
    },
    {
      id: "groups",
      label: t('tabs.groups'),
      icon: <GroupIcon sx={{ fontSize: '1.25rem' }} />,
      content: <GroupManagement />,
    },
    {
      id: "roles",
      label: t('tabs.roles'),
      icon: <ShieldCheckIcon sx={{ fontSize: '1.25rem' }} />,
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