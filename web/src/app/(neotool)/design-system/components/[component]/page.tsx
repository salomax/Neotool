"use client";

import React, { use } from "react";
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import Paper from '@mui/material/Paper';
import Link from "next/link";
import { useResponsive } from "@/shared/hooks/useResponsive";
import dynamic from 'next/dynamic';
import { getComponentDocs } from './docs/registry';

// Extract category from component's githubUrl path
const getCategoryFromGithubUrl = (githubUrl: string): string => {
  const match = githubUrl.match(/\/ui\/([^\/]+)/);
  return match?.[1] ?? 'primitives';
};

// Lazy load syntax highlighter to reduce initial bundle size
const CodeHighlighter = dynamic(() => 
  import('react-syntax-highlighter').then(mod => {
    const { Prism } = mod;
    return {
      default: ({ children, ...props }: any) => {
        const [style, setStyle] = React.useState<any>(null);
        
        React.useEffect(() => {
          import('react-syntax-highlighter/dist/esm/styles/prism').then(styleMod => {
            setStyle(styleMod.vscDarkPlus);
          }).catch(err => {
            console.error('Failed to load syntax highlighter style:', err);
          });
        }, []);
        
        if (!style) return <div>Loading code...</div>;
        
        return (
          <Prism style={style} {...props}>
            {children}
          </Prism>
        );
      }
    };
  }), 
  { 
    ssr: false,
    loading: () => <div>Loading code...</div>
  }
);

interface ComponentPageProps {
  params: Promise<{
    component: string;
  }>;
}

export default function ComponentPage({ params }: ComponentPageProps) {
  const { isMobile } = useResponsive();
  const resolvedParams = use(params);
  const componentName = resolvedParams.component;

  // Get component documentation from registry
  const componentDocs = getComponentDocs(componentName);
  
  if (!componentDocs) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box sx={{ mb: 4 }}>
          <Button
            component={Link}
            href="/design-system/components"
            startIcon={<ArrowBackIcon />}
            sx={{ mb: 3 }}
          >
            Back to Components
          </Button>
          <Typography variant="h3" component="h1" gutterBottom>
            Component Not Found
          </Typography>
          <Typography variant="body1" color="text.secondary">
            The component "{componentName}" could not be found in the documentation.
          </Typography>
        </Box>
      </Container>
    );
  }
  
  const componentData = componentDocs.data;

  // Copy code functionality
  const handleCopyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      alert('Code copied to clipboard!');
    } catch (err) {
      console.error('Failed to copy: ', err);
      alert('Failed to copy to clipboard');
    }
  };

  // Get import statement
  const getImportPath = (githubUrl: string) => {
    return githubUrl.replace('/web/src', '@');
  };
  const importPath = getImportPath(componentData.githubUrl);
  const importStatement = `import { ${componentData.name} } from '${importPath}';`;

  // Get Storybook URL - map component name to story path
  // Storybook paths are: Components/{Category}/{ComponentName}
  // Story files are in: components/{category}/{ComponentName}.stories.tsx
  const getStorybookPath = (name: string, data: any) => {
    const nameLower = name.toLowerCase();
    
    // Extract category from githubUrl path
    const category = getCategoryFromGithubUrl(data.githubUrl);
    
    // Convert component name to Storybook format
    // Remove 'Field' suffix and capitalize properly
    let componentName = name;
    if (nameLower.endsWith('field')) {
      componentName = name.slice(0, -5); // Remove 'Field'
    }
    // Handle special cases
    const nameMapping: Record<string, string> = {
      'textfield': 'input',
      'selectfield': 'select',
      'checkboxfield': 'checkbox',
      'datepickerfield': 'datepicker',
      'autocompletefield': 'autocomplete',
    };
    componentName = nameMapping[nameLower] || componentName;
    
    // Format: Components/Primitives/Button -> components-primitives-button
    return `http://localhost:6006/?path=/story/components-${category}-${componentName.toLowerCase()}`;
  };
  
  const storybookUrl = getStorybookPath(componentName, componentData);

  // Get code example
  const getCodeExample = React.useMemo(() => {
    return (exampleTitle: string): string => {
      return componentDocs.examples[exampleTitle] || '';
    };
  }, [componentDocs]);

  // Get component renderer
  const ComponentRenderer = componentDocs?.renderer;

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Button
          component={Link}
          href="/design-system/components"
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 3 }}
        >
          Back to Components
        </Button>
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h3" component="h1" gutterBottom>
              {componentData.name}
            </Typography>
            <Typography variant="h6" color="text.secondary" sx={{ mb: 2 }}>
              {componentData.description}
            </Typography>
          </Box>
          
          {/* Storybook Link */}
          {storybookUrl && (
            <Button
              variant="outlined"
              href={storybookUrl}
              target="_blank"
              rel="noopener noreferrer"
              endIcon={<OpenInNewIcon />}
              sx={{ ml: 2 }}
            >
              View in Storybook
            </Button>
          )}
        </Box>

        {/* Import Statement */}
        <Paper 
          variant="outlined" 
          sx={{ 
            p: 2, 
            mb: 3,
            bgcolor: 'background.default',
            position: 'relative'
          }}
        >
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
              Import
            </Typography>
            <IconButton
              size="small"
              onClick={() => handleCopyCode(importStatement)}
              sx={{
                color: 'text.secondary',
                '&:hover': {
                  bgcolor: 'action.hover'
                }
              }}
            >
              <ContentCopyIcon fontSize="small" />
            </IconButton>
          </Box>
          <CodeHighlighter
            language="typescript"
            customStyle={{
              margin: 0,
              fontSize: "0.875rem",
              lineHeight: "1.5",
              background: 'transparent',
              padding: 0
            }}
            showLineNumbers={false}
            wrapLines={true}
            wrapLongLines={true}
          >
            {importStatement}
          </CodeHighlighter>
        </Paper>
      </Box>

      {/* Main Content */}
      <Box>
        {/* Examples */}
        <Card sx={{ mb: 4 }}>
          <CardContent>
            <Typography variant="h5" gutterBottom>
              Examples
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Interactive examples and code snippets
            </Typography>
            
            {componentData.examples.map((example: any, index: number) => {
              const codeExample = getCodeExample ? getCodeExample(example.title) : '';
              
              return (
                <Box key={index} sx={{ mb: 4 }}>
                  <Typography variant="h6" gutterBottom>
                    {example.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    {example.description}
                  </Typography>
                  
                  {/* Example Preview */}
                  <Box sx={{ 
                    p: 3, 
                    bgcolor: "background.paper", 
                    borderRadius: 1, 
                    border: "1px solid",
                    borderColor: "divider",
                    mb: 2,
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    minHeight: 100
                  }}>
                    {ComponentRenderer ? (
                      <ComponentRenderer example={example.title} />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        Renderer not available - component needs to be migrated
                      </Typography>
                    )}
                  </Box>
                  
                  {/* Code Example */}
                  {codeExample && (
                    <Box sx={{ 
                      borderRadius: 1,
                      overflow: "hidden",
                      border: "1px solid",
                      borderColor: "divider",
                      position: "relative"
                    }}>
                      <Box sx={{
                        position: "absolute",
                        top: 8,
                        right: 8,
                        zIndex: 1
                      }}>
                        <IconButton
                          size="small"
                          onClick={() => handleCopyCode(codeExample)}
                          sx={{
                            backgroundColor: "rgba(0, 0, 0, 0.5)",
                            color: "white",
                            "&:hover": {
                              backgroundColor: "rgba(0, 0, 0, 0.7)"
                            }
                          }}
                        >
                          <ContentCopyIcon fontSize="small" />
                        </IconButton>
                      </Box>
                      <CodeHighlighter
                        language="tsx"
                        customStyle={{
                          margin: 0,
                          fontSize: "0.875rem",
                          lineHeight: "1.5"
                        }}
                        showLineNumbers={false}
                        wrapLines={true}
                        wrapLongLines={true}
                      >
                        {codeExample}
                      </CodeHighlighter>
                    </Box>
                  )}
                </Box>
              );
            })}
          </CardContent>
        </Card>

        {/* Props Documentation */}
        <Card>
          <CardContent>
            <Typography variant="h5" gutterBottom>
              Props
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Available properties and their descriptions
            </Typography>
            
            <Box sx={{ overflow: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ borderBottom: "1px solid #e0e0e0" }}>
                    <th style={{ textAlign: "left", padding: "8px", fontWeight: 600 }}>Name</th>
                    <th style={{ textAlign: "left", padding: "8px", fontWeight: 600 }}>Type</th>
                    <th style={{ textAlign: "left", padding: "8px", fontWeight: 600 }}>Required</th>
                    <th style={{ textAlign: "left", padding: "8px", fontWeight: 600 }}>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {componentData.props.map((prop: any, index: number) => (
                    <tr key={index} style={{ borderBottom: "1px solid #f0f0f0" }}>
                      <td style={{ padding: "8px", fontFamily: "monospace" }}>{prop.name}</td>
                      <td style={{ padding: "8px", fontFamily: "monospace", fontSize: "0.875rem" }}>{prop.type}</td>
                      <td style={{ padding: "8px" }}>
                        <Chip 
                          label={prop.required ? "Yes" : "No"} 
                          size="small" 
                          color={prop.required ? "error" : "default"}
                        />
                      </td>
                      <td style={{ padding: "8px" }}>{prop.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Box>
          </CardContent>
        </Card>
      </Box>
    </Container>
  );
}
