# Logical layers illustration

Generated with the built-in image generation tool. The diagram illustrates the working proposal in the README; update it when layer names or relationships change.

## Generation prompt

Create one precise technical architecture diagram for the Neotool README, in Excalidraw whiteboard style: clean white background, subtly hand-drawn rectangular boxes, dark charcoal thin strokes, highly readable large handwritten lettering, restrained pale blue and teal fills. Landscape aspect ratio around 3:2, high resolution. This is an information diagram, no people, robots, buildings, decorative objects or metaphorical illustrations. All text in English, crisp and correct. Title: "Neotool — Logical layers". Small subtitle: "Logical responsibilities · working proposal".

Layout: a clear main vertical flow in the middle-left with seven numbered, wide rectangular nodes from top to bottom, generous spacing for connectors. Each node has a bold title and one small line of examples. Use these exact titles and contents:
"1. Product strategy and context" / "Problems · Evidence · Hypotheses · Decisions · Outcomes"
"2. Experience and design" / "Journeys · States · Accessibility · Tokens · Prototypes"
"3. Solution architecture" / "Domains · Boundaries · Contracts · Invariants · ADRs"
"4. Delivery" / "Specifications · Acceptance criteria · Plans · Work items"
"5. Agent execution" / "Context · Skills · Harness · CLI · Tools · Checkpoints"
"6. Product implementation" / "Applications · Modules · Services · Data · Infrastructure"
"7. Validation and operation" / "Checks · Releases · Telemetry · Runbooks · Incidents"

Connect adjacent boxes with downward arrows to show the main flow, labeled compactly: "informs", "constrains", "guides", "coordinates", "supports", "verifies". These arrows describe information flow, NOT a required waterfall or software dependency graph. Draw one clearly visible routed return arrow along the far left from node 7 to node 1, labeled "Evidence and learning"; small branches may return into nodes 2 and 3 if space allows, but avoid visual clutter.

On the far right draw one tall pale teal panel spanning the full height of all seven boxes. Heading "Across all layers". Include exactly these five short stacked lines: "Shared knowledge", "Traceability", "Security", "Quality", "Governance". A slim vertical brace beside this panel spans nodes 1 to 7 to indicate cross-cutting applicability. Keep it visually distinct from the main flow. Below the diagram add the footer "People and agents collaborate across layers. Feedback revisits earlier decisions." Ensure excellent legibility, sufficient margins, aligned boxes, no clipped text, no extra boxes or layers, no brand logos, no watermark.

## Correction prompt

Edit this technical diagram with exactly one textual correction: replace the connector label "verifies" between box 6 "Product implementation" and box 7 "Validation and operation" with "is checked by". Preserve every other word, box, arrow, line, color, placement and layout exactly. Keep the existing opaque white background and Excalidraw-style lettering.
