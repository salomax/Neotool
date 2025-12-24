"use client";

import React, { useState, useCallback, useRef } from "react";
import AddIcon from "@mui/icons-material/Add";
import { useGroupManagement, type Group } from "@/shared/hooks/authorization/useGroupManagement";
import { GroupSearch } from "./GroupSearch";
import { GroupList } from "./GroupList";
import { GroupDrawer } from "./GroupDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { DeleteConfirmationDialog } from "@/shared/components/ui/feedback";
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";
import {
  ManagementHeaderActions,
  ManagementHeaderActionButton,
} from "@/shared/components/management/ManagementHeaderActions";
import { Box } from "@/shared/components/ui/layout";
import { PermissionGate } from "@/shared/components/authorization";

export interface GroupManagementProps {
  initialSearchQuery?: string;
}

/**
 * GroupManagement orchestrates measurement + content rendering.
 */
export const GroupManagement: React.FC<GroupManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const [initialPageSize, setInitialPageSize] = useState<number | null>(null);

  const handleMeasurement = useCallback((size: number) => {
    if (size > 0) {
      setInitialPageSize((prev) => prev ?? size);
    }
  }, []);

  if (initialPageSize === null) {
    return <GroupManagementSizer onMeasured={handleMeasurement} />;
  }

  return (
    <GroupManagementContent
      initialPageSize={initialPageSize}
      initialSearchQuery={initialSearchQuery}
    />
  );
};

const GroupManagementContent: React.FC<{
  initialPageSize: number;
  initialSearchQuery: string;
}> = ({ initialPageSize, initialSearchQuery }) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerGroupId, setDrawerGroupId] = useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [groupToDelete, setGroupToDelete] = useState<Group | null>(null);

  const {
    groups,
    searchQuery,
    inputValue,
    handleInputChange,
    handleSearch,
    pageInfo,
    paginationRange,
    canLoadPreviousPage,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    loading,
    error,
    refetch,
    createGroup,
    updateGroup,
    deleteGroup,
    createLoading,
    updateLoading,
    deleteLoading,
    orderBy,
    handleSort,
    setFirst,
  } = useGroupManagement({
    initialSearchQuery,
    initialFirst: initialPageSize,
  });

  const handleCreate = useCallback(() => {
    setDrawerGroupId(null);
    setDrawerOpen(true);
  }, []);

  const handleEdit = useCallback((group: Group) => {
    setDrawerGroupId(group.id);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setDrawerGroupId(null);
  }, []);

  const handleTableResize = useCallback(
    (pageSize: number) => {
      if (pageSize > 0) {
        setFirst(pageSize);
      }
    },
    [setFirst]
  );

  const handleDeleteClick = useCallback((group: Group) => {
    setGroupToDelete(group);
    setDeleteConfirmOpen(true);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (groupToDelete) {
      try {
        await deleteGroup(groupToDelete.id);
        toast.success(t("groupManagement.toast.groupDeleted", { name: groupToDelete.name }));
        setDeleteConfirmOpen(false);
        setGroupToDelete(null);
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.toast.groupDeleteError")
        );
        toast.error(errorMessage);
      }
    }
  }, [groupToDelete, deleteGroup, toast, t]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteConfirmOpen(false);
    setGroupToDelete(null);
  }, []);

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <ManagementHeaderActions>
          <Box sx={{ flexGrow: 1 }} maxWidth="sm">
            <GroupSearch
              value={inputValue}
              onChange={handleInputChange}
              onSearch={handleSearch}
              placeholder={t("groupManagement.searchPlaceholder")}
            />
          </Box>
          <PermissionGate require="security:group:save">
            <ManagementHeaderActionButton
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleCreate}
              data-testid="create-group-button"
            >
              {t("groupManagement.newButton")}
            </ManagementHeaderActionButton>
          </PermissionGate>
        </ManagementHeaderActions>
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <GroupList
          groups={groups}
          loading={loading}
          onEdit={handleEdit}
          onDelete={handleDeleteClick}
          emptyMessage={
            searchQuery
              ? t("groupManagement.emptySearchResults")
              : t("groupManagement.emptyList")
          }
          pageInfo={pageInfo}
          paginationRange={paginationRange}
          onLoadNext={loadNextPage}
          onLoadPrevious={loadPreviousPage}
          onGoToFirst={goToFirstPage}
          canLoadPreviousPage={canLoadPreviousPage}
          onTableResize={handleTableResize}
          recalculationKey={`${groups.length}-${loading ? "loading" : "ready"}`}
          orderBy={orderBy}
          onSortChange={handleSort}
        />
        <DeleteConfirmationDialog
          open={deleteConfirmOpen}
          item={groupToDelete}
          loading={deleteLoading}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          titleKey="groupManagement.deleteDialog.title"
          messageKey="groupManagement.deleteDialog.message"
          cancelKey="groupManagement.deleteDialog.cancel"
          deleteKey="groupManagement.deleteDialog.delete"
          deletingKey="groupManagement.deleteDialog.deleting"
          t={t}
        />
      </ManagementLayout.Content>
      <ManagementLayout.Drawer>
        <GroupDrawer
          open={drawerOpen}
          onClose={handleCloseDrawer}
          groupId={drawerGroupId}
        />
      </ManagementLayout.Drawer>
    </ManagementLayout>
  );
};

const GroupManagementSizer: React.FC<{ onMeasured: (size: number) => void }> = ({
  onMeasured,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const hasReported = useRef(false);

  const handleTableResize = useCallback(
    (size: number) => {
      if (size > 0 && !hasReported.current) {
        hasReported.current = true;
        onMeasured(size);
      }
    },
    [onMeasured]
  );

  return (
    <ManagementLayout
      error={undefined}
      onErrorRetry={undefined}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <ManagementHeaderActions>
          <Box sx={{ flexGrow: 1 }} maxWidth="sm">
            <GroupSearch
              value=""
              onChange={() => {}}
              onSearch={() => {}}
              placeholder={t("groupManagement.searchPlaceholder")}
            />
          </Box>
          <PermissionGate require="security:group:save">
            <ManagementHeaderActionButton
              variant="contained"
              startIcon={<AddIcon />}
              disabled
              data-testid="create-group-button"
            >
              {t("groupManagement.newButton")}
            </ManagementHeaderActionButton>
          </PermissionGate>
        </ManagementHeaderActions>
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <GroupList
          groups={[]}
          loading
          onEdit={() => {}}
          onDelete={undefined}
          emptyMessage={t("groupManagement.emptyList")}
          pageInfo={null}
          paginationRange={undefined}
          onTableResize={handleTableResize}
          recalculationKey="group-measurement"
        />
      </ManagementLayout.Content>
    </ManagementLayout>
  );
};
