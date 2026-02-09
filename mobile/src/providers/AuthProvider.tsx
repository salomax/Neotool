import { ReactNode, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import { AuthContext, User } from '@/hooks/useAuth';
import { clearApolloCache } from '@/lib/apollo-client';

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Check if user is already logged in
    loadUser();
  }, []);

  const loadUser = async () => {
    try {
      const token = await SecureStore.getItemAsync('accessToken');
      if (token) {
        // TODO: Validate token and fetch user data
        // For now, just set a mock user
        setUser({
          id: '1',
          email: 'user@example.com',
          fullName: 'John Doe',
        });
      }
    } catch (error) {
      console.error('Failed to load user:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const login = async (email: string, password: string) => {
    try {
      setIsLoading(true);

      // TODO: Call login mutation
      // const result = await loginMutation({ email, password });

      // Mock login for now
      const mockToken = 'mock-access-token';
      const mockRefreshToken = 'mock-refresh-token';
      const mockUser: User = {
        id: '1',
        email,
        fullName: 'John Doe',
      };

      await SecureStore.setItemAsync('accessToken', mockToken);
      await SecureStore.setItemAsync('refreshToken', mockRefreshToken);

      setUser(mockUser);
    } catch (error) {
      console.error('Login failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      setIsLoading(true);

      // Clear tokens
      await SecureStore.deleteItemAsync('accessToken');
      await SecureStore.deleteItemAsync('refreshToken');

      // Clear Apollo cache
      await clearApolloCache();

      setUser(null);
    } catch (error) {
      console.error('Logout failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const signup = async (email: string, password: string, fullName: string) => {
    try {
      setIsLoading(true);

      // TODO: Call signup mutation

      // For now, just call login
      await login(email, password);
    } catch (error) {
      console.error('Signup failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, signup }}>
      {children}
    </AuthContext.Provider>
  );
}
