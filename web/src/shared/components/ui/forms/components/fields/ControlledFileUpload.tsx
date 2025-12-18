// web/src/components/form/fields/ControlledFileUpload.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { Box, Stack, Typography, IconButton } from "@mui/material";
import UploadIcon from "@mui/icons-material/Upload";
import DeleteIcon from "@mui/icons-material/Delete";

export type ControlledFileUploadProps = {
  control: Control<any>;
  name: string;
  label?: string;
  description?: string;
  multiple?: boolean;
  accept?: string; // e.g. "image/*,.pdf"
  maxFiles?: number;
  maxSizeMB?: number;
  /** Transform files before saving to form (e.g., keep only metadata) */
  transformOut?: (files: File[]) => any;
};

function formatBytes(bytes: number): string {
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(1)} ${units[i]}`;
}

function PreviewList({
  files,
  onRemove,
}: {
  files: File[];
  onRemove: (idx: number) => void;
}) {
  const [urls, setUrls] = React.useState<string[]>([]);
  React.useEffect(() => {
    const next = files.map((f) => URL.createObjectURL(f));
    setUrls(next);
    return () => next.forEach((u) => URL.revokeObjectURL(u));
  }, [files]);
  return (
    <Stack spacing={1}>
      {files.map((f, idx) => {
        const isImg = f.type?.startsWith("image/");
        return (
          <Stack
            key={idx}
            direction="row"
            spacing={1}
            alignItems="center"
            sx={{ border: "1px solid #e0e0e0", p: 1, borderRadius: 1 }}
          >
            {isImg ? (
              <Box
                component="img"
                src={urls[idx]}
                alt={f.name}
                sx={{
                  width: 56,
                  height: 56,
                  objectFit: "cover",
                  borderRadius: 1,
                  border: "1px solid #ddd",
                }}
              />
            ) : (
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  borderRadius: 1,
                  border: "1px solid #ddd",
                  display: "grid",
                  placeItems: "center",
                  fontSize: 12,
                }}
              >
                {f.type || "file"}
              </Box>
            )}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="body2" noWrap title={f.name}>
                {f.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatBytes(f.size)}
              </Typography>
            </Box>
            <IconButton aria-label="remove file" onClick={() => onRemove(idx)}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
        );
      })}
    </Stack>
  );
}

export default function ControlledFileUpload({
  control,
  name,
  label = "Upload files",
  description,
  multiple,
  accept,
  maxFiles,
  maxSizeMB = 10,
  transformOut,
}: ControlledFileUploadProps) {
  const inputRef = React.useRef<HTMLInputElement | null>(null);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const files: File[] = Array.isArray(field.value)
          ? field.value
          : field.value
            ? [field.value]
            : [];
        const setFiles = (arr: File[]) => {
          const next =
            typeof transformOut === "function"
              ? transformOut(arr)
              : multiple
                ? arr
                : (arr[0] ?? null);
          field.onChange(next);
        };

        const openPicker = () => inputRef.current?.click();

        const onDrop = (e: React.DragEvent) => {
          e.preventDefault();
          const list = Array.from(e.dataTransfer.files || []);
          handleAdd(list);
        };

        const handleAdd = (list: File[]) => {
          let next = multiple ? [...files, ...list] : list.slice(0, 1);
          if (typeof maxFiles === "number") next = next.slice(0, maxFiles);
          // filter by size
          const maxBytes = maxSizeMB * 1024 * 1024;
          next = next.filter((f) => f.size <= maxBytes);
          setFiles(next);
        };

        const onInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
          const list = Array.from(e.target.files || []);
          handleAdd(list);
          e.currentTarget.value = ""; // allow selecting same file again
        };

        const onRemove = (idx: number) => {
          const next = files.filter((_file, i) => i !== idx);
          setFiles(next);
        };

        return (
          <Stack spacing={1}>
            <Typography variant="subtitle2">{label}</Typography>
            {description && (
              <Typography variant="body2" color="text.secondary">
                {description}
              </Typography>
            )}

            <Box
              onDragOver={(e) => e.preventDefault()}
              onDrop={onDrop}
              onClick={openPicker}
              sx={{
                border: "1px dashed",
                borderColor: fieldState.error ? "error.main" : "divider",
                borderRadius: 1,
                p: 2,
                textAlign: "center",
                cursor: "pointer",
                bgcolor: "action.hover",
              }}
            >
              <UploadIcon sx={{ mb: 1 }} />
              <Typography variant="body2">
                Drag & drop or click to select
              </Typography>
              {accept && (
                <Typography variant="caption" color="text.secondary">
                  Accepted: {accept}
                </Typography>
              )}
            </Box>

            <input
              ref={inputRef}
              type="file"
              hidden
              multiple={!!multiple}
              accept={accept}
              onChange={onInputChange}
            />

            {!!files.length && (
              <PreviewList files={files} onRemove={onRemove} />
            )}

            {fieldState.error && (
              <Typography variant="caption" color="error">
                {fieldState.error.message}
              </Typography>
            )}
          </Stack>
        );
      }}
    />
  );
}
