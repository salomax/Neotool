import { NextRequest, NextResponse } from 'next/server'

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const resolvedParams = await params
  const path = resolvedParams.path.join('/')
  
  // Handle source map requests that don't exist
  if (path.endsWith('.map')) {
    // Decode the path to handle encoded characters
    const decodedPath = decodeURIComponent(path)
    const fileName = decodedPath.replace('.map', '')
    
    // Return a valid source map to prevent 404 errors
    const emptySourceMap = {
      version: 3,
      sources: [fileName],
      names: [],
      mappings: 'AAAA',
      file: fileName,
      sourcesContent: ['// Source map placeholder'],
    }
    
    return new NextResponse(JSON.stringify(emptySourceMap), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'X-Content-Type-Options': 'nosniff',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET',
        'Access-Control-Allow-Headers': 'Content-Type',
      },
    })
  }
  
  // For non-source map requests, return 404
  return new NextResponse('Not Found', { status: 404 })
}

// Handle OPTIONS requests for CORS
export async function OPTIONS() {
  return new NextResponse(null, {
    status: 200,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    },
  })
}
