import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import {
  Box,
  Tab,
  Tabs as MuiTabs,
  Tooltip,
  Typography,
  Chip,
  Fade,
  useTheme,
  useMediaQuery,
  SxProps,
  Theme
} from '@mui/material';
import {
  Add as AddIcon,
  Close as CloseIcon,
  DragIndicator as DragIndicatorIcon
} from '@mui/icons-material';
import { v4 as uuidv4 } from 'uuid';

export interface TabItem {
  id: string;
  label: string;
  content: React.ReactNode;
  closable?: boolean;
  disabled?: boolean;
  icon?: React.ReactNode;
  badge?: string | number;
  tooltip?: string;
}

export interface TabsProps {
  tabs: TabItem[];
  value?: string;
  onChange?: (tabId: string) => void;
  onTabsChange?: (tabs: TabItem[]) => void;
  showAddButton?: boolean;
  showCloseButtons?: boolean;
  draggable?: boolean;
  orientation?: 'horizontal' | 'vertical';
  variant?: 'standard' | 'scrollable' | 'fullWidth';
  indicatorColor?: 'primary' | 'secondary';
  textColor?: 'primary' | 'secondary' | 'inherit';
  showBadges?: boolean;
  maxTabs?: number;
  className?: string;
  'data-testid'?: string;
  sx?: SxProps<Theme>;
}

const Tabs: React.FC<TabsProps> = ({
  tabs: initialTabs,
  value,
  onChange,
  onTabsChange,
  showAddButton = false,
  showCloseButtons = false,
  draggable = false,
  orientation = 'horizontal',
  variant = 'scrollable',
  indicatorColor = 'primary',
  textColor = 'primary',
  showBadges = true,
  maxTabs = 10,
 className,
  'data-testid': testId = 'tabs',
  sx
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const tabsContainerRef = useRef<HTMLDivElement>(null);
  
  // Internal state management - igual ao seu código original
  const [tabs, setTabs] = useState<TabItem[]>(initialTabs);
  const [activeTab, setActiveTab] = useState(() => {
    return value || initialTabs[0]?.id || '';
  });
  const [shouldScroll, setShouldScroll] = useState(false);
  const [draggedTab, setDraggedTab] = useState<string | null>(null);
  
  // Sync with external tabs when they change - igual ao seu código
  useEffect(() => {
    setTabs(initialTabs);
  }, [initialTabs]);

  // Check if tabs overflow and need scrolling
  useEffect(() => {
    if (tabsContainerRef.current) {
      const checkOverflow = () => {
        const containerWidth = tabsContainerRef.current?.clientWidth || 0;
        const tabsWidth = tabsContainerRef.current?.scrollWidth || 0;
        setShouldScroll(tabsWidth > containerWidth);
      };

      checkOverflow();

      // Add resize observer to handle window resizing
      const resizeObserver = new ResizeObserver(checkOverflow);
      resizeObserver.observe(tabsContainerRef.current);

      return () => {
        resizeObserver.disconnect();
      };
    }
  }, [tabs]);
  
  // Sync with external value prop
  useEffect(() => {
    if (value !== undefined) {
      setActiveTab(value);
    }
  }, [value]);
  
  // Ensure activeTab is never set to "add-tab" - igual ao seu código
  useEffect(() => {
    if (activeTab === 'add-tab') {
      const firstRealTab = tabs[0]?.id;
      if (firstRealTab) {
        setActiveTab(firstRealTab);
      }
    }
  }, [activeTab, tabs]);

  const handleTabChange = useCallback((event: React.SyntheticEvent, newValue: string) => {
    // Don't handle the add tab as a regular tab - igual ao seu código
    if (newValue === 'add-tab') {
      return;
    }
    setActiveTab(newValue);
    onChange?.(newValue);
  }, [onChange]);

  const addNewTab = useCallback(({
    id = uuidv4(), 
    index = tabs.length + 1
  }: {
    id?: string;
    index?: number;
  } = {}) => {
    return {
      id: `tab-${id}`,
      label: `New Tab ${index}`,
      content: <div>New Tab Content</div>,
      closable: true
    };
  }, [tabs.length]);

  const handleTabClose = useCallback((event: React.MouseEvent, tabId: string) => {
    event.stopPropagation();
    event.preventDefault();
    
    const tabIndex = tabs.findIndex(tab => tab.id === tabId);
    const isActiveTab = activeTab === tabId;
    
    // Remove the tab - igual ao seu código
    const newTabs = tabs.filter(tab => tab.id !== tabId);

    // If we removed the active tab, switch to another tab - igual ao seu código
    if (isActiveTab && newTabs.length > 0) {
      const nextTab = tabIndex > 0 ? newTabs[tabIndex - 1] : newTabs[tabIndex] || newTabs[0];
      if (nextTab) {
        setActiveTab(nextTab.id);
        onChange?.(nextTab.id);
      }
    } else if (newTabs.length === 0) {
      // Se não há abas, cria uma nova - igual ao seu código
      const newTab = addNewTab({index: 1});
      newTabs.push(newTab);
      setActiveTab(newTab.id);
      onChange?.(newTab.id);
    }
    
    // Update internal state and notify parent - igual ao seu código
    setTabs(newTabs);
    onTabsChange?.(newTabs);
  }, [activeTab, tabs, onChange, onTabsChange, addNewTab]);

  const handleTabAdd = useCallback(() => {
    if (tabs.length < maxTabs) {
      const newTab = addNewTab();
      const newTabs = [...tabs, newTab];
      setTabs(newTabs);
      setActiveTab(newTab.id);
      onChange?.(newTab.id);
      onTabsChange?.(newTabs);
    }
  }, [tabs, maxTabs, onChange, onTabsChange, addNewTab]);

  const handleDragStart = useCallback((event: React.DragEvent, tabId: string) => {
    if (!draggable) return;
    setDraggedTab(tabId);
    event.dataTransfer.effectAllowed = 'move';
  }, [draggable]);

  const handleDragOver = useCallback((event: React.DragEvent) => {
    if (!draggable) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, [draggable]);

  const handleDrop = useCallback((event: React.DragEvent, targetTabId: string) => {
    if (!draggable || !draggedTab) return;
    
    event.preventDefault();
    
    if (draggedTab !== targetTabId) {
      const draggedIndex = tabs.findIndex(tab => tab.id === draggedTab);
      const targetIndex = tabs.findIndex(tab => tab.id === targetTabId);
      
      if (draggedIndex !== -1 && targetIndex !== -1) {
        const newTabs = [...tabs];
        const [draggedTabItem] = newTabs.splice(draggedIndex, 1);
        newTabs.splice(targetIndex, 0, draggedTabItem as TabItem);
        
        setTabs(newTabs);
        onTabsChange?.(newTabs);
      }
    }
    
    setDraggedTab(null);
  }, [draggable, draggedTab, tabs, onTabsChange]);

  const renderTabLabel = useCallback((tab: TabItem) => {
    const hasBadge = showBadges && (tab.badge !== undefined && tab.badge !== null);
    const hasIcon = tab.icon !== undefined;
    const isSelected = activeTab === tab.id;
    
    const labelContent = (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          minWidth: 0,
          flex: 1
        }}
        draggable={draggable}
        onDragStart={draggable ? (e) => handleDragStart(e, tab.id) : undefined}
        onDragOver={draggable ? handleDragOver : undefined}
        onDrop={draggable ? (e) => handleDrop(e, tab.id) : undefined}
      >
        {draggable && (
          <DragIndicatorIcon 
            fontSize="small" 
            sx={{ opacity: 0.6, cursor: 'grab' }} 
          />
        )}
        
        {hasIcon && (
          <Box sx={{ display: 'flex', alignItems: 'center', minWidth: 'fit-content' }}>
            {tab.icon}
          </Box>
        )}
        
        <Typography
          variant="body2"
          sx={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            minWidth: 0,
            flex: 1,
            fontWeight: isSelected ? 'bold' : 'normal'
          }}
        >
          {tab.label}
        </Typography>
        
        {hasBadge && (
          <Chip
            label={tab.badge}
            size="small"
            color="primary"
            sx={{
              height: 16,
              minWidth: 16,
              fontSize: '0.75rem',
              '& .MuiChip-label': {
                px: 0.5
              }
            }}
          />
        )}
        
        {tab.closable !== false && showCloseButtons && (
          <Box
            onClick={(e) => handleTabClose(e, tab.id)}
            sx={{
              ml: 0.5,
              p: 0.25,
              opacity: 0.7,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: '50%',
              minWidth: 24,
              minHeight: 24,
              '&:hover': {
                opacity: 1,
                backgroundColor: 'action.hover'
              },
              '&:focus-visible': {
                outline: '2px solid',
                outlineColor: 'primary.main',
                outlineOffset: 1
              }
            }}
            data-testid={`tab-close-${tab.id}`}
            role="button"
            tabIndex={0}
            aria-label={`Close ${tab.label} tab`}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                handleTabClose(e as any, tab.id);
              }
            }}
          >
            <CloseIcon fontSize="small" />
          </Box>
        )}
      </Box>
    );
    
    return tab.tooltip ? (
      <Tooltip title={tab.tooltip}>
        {labelContent}
      </Tooltip>
    ) : labelContent;
  }, [showBadges, showCloseButtons, draggable, activeTab, handleTabClose, handleDragStart, handleDragOver, handleDrop]);

  // Memoiza o conteúdo da aba ativa
  const activeTabContent = useMemo(() => {
    return tabs.find(tab => tab.id === activeTab)?.content;
  }, [tabs, activeTab]);

  // Garante que o activeTab seja sempre válido para o MUI
  // Use useMemo to ensure it's computed after tabs are set
  const validActiveTab = useMemo(() => {
    if (tabs.length === 0) {
      return false; // MUI Tabs expects false when no tabs
    }
    const foundTab = tabs.find(tab => tab.id === activeTab);
    return foundTab ? activeTab : (tabs[0]?.id || false);
  }, [tabs, activeTab]);

  return (
    <Box
      className={className}
      data-testid={testId}
      sx={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: orientation === 'vertical' ? 'row' : 'column'
      }}
    >
      <Box>
        <MuiTabs
          ref={tabsContainerRef}
          value={validActiveTab}
          onChange={handleTabChange}
          orientation={orientation}
          variant={shouldScroll || isMobile ? 'scrollable' : variant}
          scrollButtons={shouldScroll || isMobile ? 'auto' : 'auto'}
          indicatorColor={indicatorColor}
          textColor={textColor}
          sx={{
            mx: 2,
            minHeight: orientation === 'vertical' ? 'auto' : 48,
            borderBottom: orientation === 'vertical' ? 'none' : `1px solid ${(theme as any).custom?.palette?.tabBorder}`,
            '& .MuiTabs-indicator': {
              display: orientation === 'vertical' ? 'none' : 'block'
            },
            '& .MuiTabs-flexContainer': {
              gap: 0,
              minWidth: shouldScroll ? 'max-content' : 'auto'
            },
            '& .MuiTabs-scrollButtons': {
              '&.Mui-disabled': {
                opacity: 0.3
              }
            },
            '& .MuiTabs-scrollableContainer': {
              overflow: 'hidden'
            }
          }}
        >
          {tabs.map((tab) => (
            <Tab
              key={tab.id}
              value={tab.id}
              label={renderTabLabel(tab)}
              disabled={tab.disabled || false}
              data-testid={`${tab.id}-tab`}
              sx={{
                minHeight: orientation === 'vertical' ? 48 : 48,
                minWidth: orientation === 'vertical' ? 120 : shouldScroll ? 120 : 'auto',
                maxWidth: orientation === 'vertical' ? 200 : shouldScroll ? 200 : 'none',
                width: shouldScroll ? 'auto' : 'auto',
                flexShrink: shouldScroll ? 0 : 1,
                textTransform: 'none',
                fontSize: '0.875rem',
                fontWeight: 500,
                px: orientation === 'vertical' ? 2 : 1.5,
                py: orientation === 'vertical' ? 1 : 0.5,
                borderBottom: 'none',
                borderRight: 'none',
                borderColor: 'divider',
                '&:last-of-type': {
                  borderBottom: 'none',
                  borderRight: 'none'
                },
                '&.Mui-selected': {
                  color: 'primary.main',
                  fontWeight: 'bold'
                },
                '&:hover': {
                  backgroundColor: 'action.hover'
                },
                '&.Mui-disabled': {
                  opacity: 0.5
                }
              }}
            />
          ))}
          
          {showAddButton && tabs.length < maxTabs && (
            <Tab
              value="add-tab"
              label={
                <Tooltip title="Add new tab">
                  <Box
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleTabAdd();
                    }}
                    sx={{
                      p: 0.5,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      borderRadius: '50%',
                      minWidth: 24,
                      minHeight: 24,
                      '&:hover': {
                        backgroundColor: 'action.hover'
                      },
                      '&:focus-visible': {
                        outline: '2px solid',
                        outlineColor: 'primary.main',
                        outlineOffset: 1
                      }
                    }}
                    data-testid="add-tab-button"
                    role="button"
                    tabIndex={0}
                    aria-label="Add new tab"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleTabAdd();
                      }
                    }}
                  >
                    <AddIcon fontSize="small" />
                  </Box>
                </Tooltip>
              }
              sx={{
                minWidth: shouldScroll ? 48 : 48,
                minHeight: 48,
                p: 0,
                flexShrink: 0,
                '&.Mui-selected': {
                  backgroundColor: 'transparent'
                }
              }}
            />
          )}
        </MuiTabs>
      </Box>
      
      {activeTabContent && (
        <Fade in timeout={200}>
          <Box
            sx={{
              flex: 1,
              p: 2,
              minHeight: 200,
              overflow: 'auto'
            }}
            data-testid="tab-content"
            role="tabpanel"
            aria-labelledby={`tab-${activeTab}`}
          >
            {activeTabContent}
          </Box>
        </Fade>
      )}
    </Box>
  );
};

export default Tabs;