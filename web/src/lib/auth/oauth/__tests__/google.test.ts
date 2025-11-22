import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { signInWithGoogle, loadGoogleIdentityServices } from '../google';

describe('Google OAuth', () => {
  beforeEach(() => {
    // Clear any existing Google script
    const existingScript = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
    if (existingScript) {
      existingScript.remove();
    }

    // Reset window.google
    delete (window as any).google;
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('loadGoogleIdentityServices', () => {
    it('should resolve immediately if Google Identity Services is already loaded', async () => {
      (window as any).google = {
        accounts: {
          id: {
            initialize: vi.fn(),
          },
        },
      };

      await expect(loadGoogleIdentityServices()).resolves.toBeUndefined();
    });

    it('should load Google Identity Services script', async () => {
      // Mock script loading
      const mockScript = {
        onload: null as (() => void) | null,
        onerror: null as (() => void) | null,
        src: '',
        async: false,
        defer: false,
      };

      const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(mockScript as any);
      const appendChildSpy = vi.spyOn(document.head, 'appendChild').mockImplementation(() => mockScript as any);

      const loadPromise = loadGoogleIdentityServices();

      // Simulate script load
      setTimeout(() => {
        (window as any).google = {
          accounts: {
            id: {
              initialize: vi.fn(),
            },
          },
        };
        if (mockScript.onload) {
          mockScript.onload();
        }
      }, 10);

      await expect(loadPromise).resolves.toBeUndefined();

      expect(createElementSpy).toHaveBeenCalledWith('script');
      expect(mockScript.src).toBe('https://accounts.google.com/gsi/client');
      expect(mockScript.async).toBe(true);
      expect(mockScript.defer).toBe(true);

      createElementSpy.mockRestore();
      appendChildSpy.mockRestore();
    });
  });

  describe('signInWithGoogle', () => {
    it('should reject if Google Identity Services is not available', async () => {
      (window as any).google = undefined;

      await expect(signInWithGoogle({ clientId: 'test-client-id' })).rejects.toThrow(
        'Google Identity Services not available'
      );
    });

    it('should initialize and trigger Google sign-in', async () => {
      const mockInitialize = vi.fn();
      const mockRenderButton = vi.fn();
      const mockCallback = vi.fn();

      (window as any).google = {
        accounts: {
          id: {
            initialize: mockInitialize,
            renderButton: mockRenderButton,
          },
        },
      };

      // Mock loadGoogleIdentityServices to resolve immediately
      vi.mocked(loadGoogleIdentityServices).mockResolvedValue(undefined);

      const signInPromise = signInWithGoogle({ clientId: 'test-client-id' });

      // Wait a bit for the initialization
      await new Promise((resolve) => setTimeout(resolve, 50));

      // Verify initialize was called
      expect(mockInitialize).toHaveBeenCalledWith({
        client_id: 'test-client-id',
        callback: expect.any(Function),
      });

      // Get the callback and call it with a credential
      const callback = mockInitialize.mock.calls[0][0].callback;
      callback({ credential: 'test-id-token' });

      await expect(signInPromise).resolves.toBe('test-id-token');
    });

    it('should reject if no credential is received', async () => {
      const mockInitialize = vi.fn();

      (window as any).google = {
        accounts: {
          id: {
            initialize: mockInitialize,
            renderButton: vi.fn(),
          },
        },
      };

      vi.mocked(loadGoogleIdentityServices).mockResolvedValue(undefined);

      const signInPromise = signInWithGoogle({ clientId: 'test-client-id' });

      await new Promise((resolve) => setTimeout(resolve, 50));

      const callback = mockInitialize.mock.calls[0][0].callback;
      callback({});

      await expect(signInPromise).rejects.toThrow('No credential received from Google');
    });
  });
});

