export const buttonExamples: Record<string, string> = {
  "Basic Usage": `<Button variant="contained" color="primary">
  Click me
</Button>`,
  "Variants": `<Button variant="contained" color="primary">
  Contained
</Button>
<Button variant="outlined" color="primary">
  Outlined
</Button>
<Button variant="text" color="primary">
  Text
</Button>`,
  "Colors": `<Button variant="contained" color="primary">
  Primary
</Button>
<Button variant="contained" color="secondary">
  Secondary
</Button>
<Button variant="contained" color="success">
  Success
</Button>
<Button variant="contained" color="error">
  Error
</Button>`,
  "Sizes": `<Button size="small" variant="contained">
  Small
</Button>
<Button size="medium" variant="contained">
  Medium
</Button>
<Button size="large" variant="contained">
  Large
</Button>`,
  "Loading": `<Button variant="contained" color="primary" loading>
  Loading...
</Button>
<Button variant="outlined" color="primary" loading loadingText="Processing">
  Submit
</Button>
<Button variant="contained" color="secondary" loading loadingText="Saving" size="small">
  Save
</Button>`,
};

