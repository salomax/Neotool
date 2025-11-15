"use client";
import * as React from "react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Paper from "@mui/material/Paper";
import InputBase from "@mui/material/InputBase";
import IconButton from "@mui/material/IconButton";
import Badge from "@mui/material/Badge";
import Avatar from "@mui/material/Avatar";
import Tooltip from "@mui/material/Tooltip";
import { alpha } from '@mui/material/styles';
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import NotificationsNoneRoundedIcon from "@mui/icons-material/NotificationsNoneRounded";

export function AppHeader() {
  const onThemeToggle = React.useCallback(() => {
    // #TODO ThemeProvider, pode trocar por um setter de contexto.
    try {
      const KEY = "ui.color-scheme";
      const cur =
        (typeof localStorage !== "undefined" &&
          (localStorage.getItem(KEY) as "light" | "dark" | null)) ||
        "light";
      const nextMode = cur === "light" ? "dark" : "light";
      if (typeof localStorage !== "undefined")
        localStorage.setItem(KEY, nextMode);
      if (typeof document !== "undefined")
        (document.documentElement as any).dataset.colorScheme = nextMode;
    } catch {
      /* empty */
    }
  }, []);

  return (
    <Box
      component="header"
      sx={{
        position: "fixed",
        top: 0,
        left: "84px", // Account for sidebar width
        right: 0,
        zIndex: (t) => t.zIndex.appBar,
        bgcolor: "background.paper",
        borderBottom: "1px solid",
        borderColor: "divider",
        backdropFilter: "blur(6px)",
      }}
    >
      <Container maxWidth="xl" sx={{ py: 1.5 }}>
        {/* grid: [esquerda vazia] [busca central] [ações à direita] */}
        <Box
          sx={{
            display: "grid",
            alignItems: "center",
            gridTemplateColumns: "1fr minmax(360px, 680px) 1fr",
            gap: 2,
          }}
        >
          <Box aria-hidden />

          {/* Busca central */}
          <Paper
            component="form"
            onSubmit={(e) => e.preventDefault()}
            sx={{
              px: 1,
              py: 0.25,
              display: "flex",
              alignItems: "center",
              borderRadius: 2,
              boxShadow: "none",
              border: "1px solid",
              borderColor: "divider",
              bgcolor: (t) =>
                alpha(
                  t.palette.common.white,
                  t.palette.mode === "light" ? 1 : 0.06,
                ),
            }}
          >
            <InputBase
              sx={{ ml: 1, flex: 1 }}
              placeholder="Search here"
              inputProps={{ "aria-label": "search" }}
            />
            <IconButton type="submit" aria-label="search">
              <SearchRoundedIcon />
            </IconButton>
          </Paper>

          {/* Ações à direita */}
          <Box
            sx={{
              justifySelf: "end",
              display: "flex",
              alignItems: "center",
              gap: 1,
            }}
          >
            <Tooltip title="Toggle theme">
              <IconButton aria-label="toggle theme" onClick={onThemeToggle}>
                <DarkModeRoundedIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title="Notifications">
              <IconButton aria-label="notifications">
                <Badge variant="dot" color="primary">
                  <NotificationsNoneRoundedIcon />
                </Badge>
              </IconButton>
            </Tooltip>
            <Tooltip title="Profile">
              <IconButton aria-label="profile">
                <Avatar sx={{ width: 32, height: 32 }}>N</Avatar>
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </Container>
    </Box>
  );
}
