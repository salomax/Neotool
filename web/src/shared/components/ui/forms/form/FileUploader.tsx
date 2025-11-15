"use client";

import * as React from "react";
import {
  Box,
  Stack,
  Typography,
  Button,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
} from "@mui/material";
import UploadIcon from "@mui/icons-material/Upload";
import DeleteIcon from "@mui/icons-material/Delete";
import ImageIcon from "@mui/icons-material/Image";
import DescriptionIcon from "@mui/icons-material/Description";
import { Controller, useFormContext } from "react-hook-form";

export type FileItem = {
  file: File;
  previewUrl?: string;
};

export type FileUploaderProps = {
  name: string;
  label?: string;
  accept?: string; // e.g. "image/*"
  multiple?: boolean;
  maxFiles?: number;
  maxSizeMb?: number;
};

function humanSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}

export const FileUploader: React.FC<FileUploaderProps> = ({
  name,
  label = "Upload files",
  accept,
  multiple,
  maxFiles = 5,
  maxSizeMb = 10,
}) => {
  const { control, setValue, getValues } = useFormContext();

  const handleFiles = async (files: FileList | null) => {
    if (!files) return;
    const arr = Array.from(files);
    const current: FileItem[] = getValues(name) ?? [];
    const next: FileItem[] = [];

    for (const f of arr) {
      if (f.size > maxSizeMb * 1024 * 1024) continue;
      const item: FileItem = { file: f };
      if (f.type.startsWith("image/")) {
        item.previewUrl = URL.createObjectURL(f);
      }
      next.push(item);
      if (current.length + next.length >= maxFiles) break;
    }

    setValue(name, [...current, ...next], {
      shouldDirty: true,
      shouldTouch: true,
    });
  };

  const removeAt = (idx: number) => {
    const current: FileItem[] = getValues(name) ?? [];
    current.splice(idx, 1);
    setValue(name, [...current], { shouldDirty: true, shouldTouch: true });
  };

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        const items: FileItem[] = field.value ?? [];
        return (
          <Stack spacing={1}>
            <Typography variant="subtitle2">{label}</Typography>
            <Box
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                handleFiles(e.dataTransfer.files);
              }}
              sx={{
                border: (theme) => `1px dashed ${theme.palette.divider}`,
                p: 2,
                borderRadius: 2,
                textAlign: "center",
                bgcolor: "background.paper",
              }}
            >
              <UploadIcon />
              <Typography variant="body2" color="text.secondary">
                Drag & drop here, or
              </Typography>
              <Button
                onClick={() =>
                  document.getElementById(`file-input-${name}`)?.click()
                }
                size="small"
              >
                Select files
              </Button>
              <input
                id={`file-input-${name}`}
                type="file"
                accept={accept}
                multiple={multiple}
                hidden
                onChange={(e) => handleFiles(e.target.files)}
              />
              <Typography
                variant="caption"
                color="text.secondary"
                display="block"
                sx={{ mt: 1 }}
              >
                Max {maxFiles} files, up to {maxSizeMb} MB each.
              </Typography>
              {fieldState.error && (
                <Typography color="error" variant="caption">
                  {fieldState.error.message}
                </Typography>
              )}
            </Box>

            {!!items.length && (
              <List dense>
                {items.map((it, idx) => (
                  <ListItem key={idx}>
                    {it.file.type.startsWith("image/") ? (
                      <ImageIcon sx={{ mr: 1 }} />
                    ) : (
                      <DescriptionIcon sx={{ mr: 1 }} />
                    )}
                    <ListItemText
                      primary={it.file.name}
                      secondary={`${humanSize(it.file.size)}${it.previewUrl ? " • preview" : ""}`}
                    />
                    <ListItemSecondaryAction>
                      <IconButton edge="end" onClick={() => removeAt(idx)}>
                        <DeleteIcon />
                      </IconButton>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}
          </Stack>
        );
      }}
    />
  );
};
