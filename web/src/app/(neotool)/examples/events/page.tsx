"use client";
import React, { useEffect, useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";

const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export default function EventsPage() {
  const [entity, setEntity] = useState("");
  const [eventType, setEventType] = useState("");
  const [rows, setRows] = useState<any[]>([]);

  async function load() {
    const url = new URL(`${API}/api/events/`);
    if (entity) url.searchParams.set("entity", entity);
    if (eventType) url.searchParams.set("eventType", eventType);
    const res = await fetch(url.toString());
    setRows(await res.json());
  }

  useEffect(() => { load(); }, [entity, eventType]);

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" gutterBottom>Event Log</Typography>
      <Paper sx={{ p:2, mb:2, display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: 2 }}>
        <TextField label="Entity (Product/Customer)" value={entity} onChange={e=>setEntity(e.target.value)} />
        <TextField label="Event Type (CREATED/UPDATED/DELETED)" value={eventType} onChange={e=>setEventType(e.target.value)} />
      </Paper>
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell><TableCell>Entity</TableCell><TableCell>Entity ID</TableCell><TableCell>Type</TableCell><TableCell>Payload</TableCell><TableCell>Created At</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map(r=>(
              <TableRow key={r.id}>
                <TableCell>{r.id}</TableCell>
                <TableCell>{r.entity}</TableCell>
                <TableCell>{r.entityId ?? "-"}</TableCell>
                <TableCell>{r.eventType}</TableCell>
                <TableCell sx={{ maxWidth: 380, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{r.payload}</TableCell>
                <TableCell>{r.createdAt}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Container>
  );
}
