/**
 * Google OAuth client wrapper using Google Identity Services.
 *
 * @see https://developers.google.com/identity/gsi/web
 */

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: {
              credential: string;
              select_by?: string;
            }) => void;
          }) => void;
          prompt: (notification?: (notification: {
            isNotDisplayed: boolean;
            isSkippedMoment: boolean;
            isDismissedMoment: boolean;
          }) => void) => void;
          renderButton: (element: HTMLElement, config: {
            theme?: 'outline' | 'filled_blue' | 'filled_black';
            size?: 'large' | 'medium' | 'small';
            text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
            shape?: 'rectangular' | 'pill' | 'circle' | 'square';
            logo_alignment?: 'left' | 'center';
            width?: number;
            locale?: string;
          }) => void;
        };
      };
    };
  }
}

export interface GoogleOAuthConfig {
  clientId: string;
}

/**
 * Load Google Identity Services script.
 * This should be called once when the app initializes.
 */
export function loadGoogleIdentityServices(): Promise<void> {
  return new Promise((resolve, reject) => {
    // Check if already loaded
    if (window.google?.accounts?.id) {
      resolve();
      return;
    }

    // Check if script is already being loaded
    const existingScript = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
    if (existingScript) {
      // Wait for it to load
      const checkInterval = setInterval(() => {
        if (window.google?.accounts?.id) {
          clearInterval(checkInterval);
          resolve();
        }
      }, 100);

      setTimeout(() => {
        clearInterval(checkInterval);
        if (window.google?.accounts?.id) {
          resolve();
        } else {
          reject(new Error('Timeout waiting for Google Identity Services to load'));
        }
      }, 5000);
      return;
    }

    // Load the script
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Failed to load Google Identity Services'));
    document.head.appendChild(script);
  });
}

/**
 * Sign in with Google using popup flow.
 * Returns a promise that resolves with the ID token (JWT).
 */
export function signInWithGoogle(config: GoogleOAuthConfig): Promise<string> {
  return new Promise((resolve, reject) => {
    // Ensure Google Identity Services is loaded
    loadGoogleIdentityServices()
      .then(() => {
        if (!window.google?.accounts?.id) {
          reject(new Error('Google Identity Services not available'));
          return;
        }

        // Create a temporary container for the button
        const container = document.createElement('div');
        container.style.position = 'fixed';
        container.style.top = '-9999px';
        container.style.left = '-9999px';
        container.style.visibility = 'hidden';
        document.body.appendChild(container);

        let resolved = false;

        // Initialize Google Identity Services
        window.google.accounts.id.initialize({
          client_id: config.clientId,
          callback: (response) => {
            if (!resolved) {
              resolved = true;
              // Clean up
              if (container.parentNode) {
                document.body.removeChild(container);
              }

              if (response.credential) {
                resolve(response.credential);
              } else {
                reject(new Error('No credential received from Google'));
              }
            }
          },
        });

        // Render button and click it programmatically to trigger popup
        try {
          window.google.accounts.id.renderButton(container, {
            theme: 'outline',
            size: 'large',
            text: 'signin_with',
            width: 250,
          });

          // Wait for button to render, then click it
          setTimeout(() => {
            if (resolved) return;

            const button = container.querySelector('div[role="button"]') as HTMLElement;
            if (button) {
              button.click();
            } else {
              // Fallback: try to find any clickable element
              const clickable = container.querySelector('[role="button"], button, a') as HTMLElement;
              if (clickable) {
                clickable.click();
              } else {
                if (!resolved) {
                  resolved = true;
                  document.body.removeChild(container);
                  reject(new Error('Failed to trigger Google sign-in popup'));
                }
              }
            }
          }, 200);
        } catch (error) {
          if (!resolved) {
            resolved = true;
            document.body.removeChild(container);
            reject(error);
          }
        }
      })
      .catch(reject);
  });
}
