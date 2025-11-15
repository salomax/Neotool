import { NextRequest, NextResponse } from 'next/server'

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl
  
  // Handle source map requests directly in middleware
  if (pathname.endsWith('.map')) {
    const fileName = pathname.replace('.map', '')
    
    // Return a valid source map response
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
  
  return NextResponse.next()
}

export const config = {
  matcher: [
    // Match all .map files
    '/((?!api|_next/static|_next/image|favicon.ico).*)',
  ],
}
