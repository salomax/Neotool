import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useOAuth } from '../useOAuth';

// Mock the Google OAuth module
vi.mock('@/lib/auth/oauth/google', () => ({
  signInWithGoogle: vi.fn(),
  loadGoogleIdentityServices: vi.fn(() => Promise.resolve()),
}));

describe('useOAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Set up environment variable
    process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID = 'test-client-id';
  });

  afterEach(() => {
    delete process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  });

  it('should initialize with correct default state', () => {
    const { result } = renderHook(() => useOAuth());

    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBe(null);
    expect(typeof result.current.signIn).toBe('function');
  });

  it('should call signInWithGoogle for google provider', async () => {
    const { signInWithGoogle } = await import('@/lib/auth/oauth/google');
    vi.mocked(signInWithGoogle).mockResolvedValue('test-id-token');

    const onSuccess = vi.fn();
    const { result } = renderHook(() =>
      useOAuth({
        onSuccess,
      })
    );

    await act(async () => {
      await result.current.signIn('google');
    });

    await waitFor(() => {
      expect(signInWithGoogle).toHaveBeenCalledWith({
        clientId: 'test-client-id',
      });
      expect(onSuccess).toHaveBeenCalledWith('test-id-token');
    });
  });

  it('should handle errors from OAuth provider', async () => {
    const { signInWithGoogle } = await import('@/lib/auth/oauth/google');
    const error = new Error('OAuth error');
    vi.mocked(signInWithGoogle).mockRejectedValue(error);

    const onError = vi.fn();
    const { result } = renderHook(() =>
      useOAuth({
        onError,
      })
    );

    await act(async () => {
      await result.current.signIn('google');
    });

    await waitFor(() => {
      expect(result.current.error).toBe(error);
      expect(onError).toHaveBeenCalledWith(error);
    });
  });

  it('should throw error for unsupported provider', async () => {
    const { result } = renderHook(() => useOAuth());

    await act(async () => {
      await result.current.signIn('microsoft' as any);
    });

    await waitFor(() => {
      expect(result.current.error).toBeTruthy();
      expect(result.current.error?.message).toContain('Unsupported OAuth provider');
    });
  });

  it('should throw error when Google client ID is not configured', async () => {
    delete process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

    const { result } = renderHook(() => useOAuth());

    await act(async () => {
      await result.current.signIn('google');
    });

    await waitFor(() => {
      expect(result.current.error).toBeTruthy();
      expect(result.current.error?.message).toContain('Google OAuth client ID is not configured');
    });
  });

  it('should set loading state during sign in', async () => {
    const { signInWithGoogle } = await import('@/lib/auth/oauth/google');
    let resolvePromise: (value: string) => void;
    const promise = new Promise<string>((resolve) => {
      resolvePromise = resolve;
    });
    vi.mocked(signInWithGoogle).mockReturnValue(promise);

    const { result } = renderHook(() => useOAuth());

    act(() => {
      result.current.signIn('google');
    });

    expect(result.current.isLoading).toBe(true);

    resolvePromise!('test-id-token');
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
  });

  it('should return null on error', async () => {
    const { signInWithGoogle } = await import('@/lib/auth/oauth/google');
    vi.mocked(signInWithGoogle).mockRejectedValue(new Error('OAuth error'));

    const { result } = renderHook(() => useOAuth());

    let idToken: string | null = null;
    await act(async () => {
      idToken = await result.current.signIn('google');
    });

    expect(idToken).toBe(null);
  });
});

