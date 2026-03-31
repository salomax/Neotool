"use client";

import * as React from "react";
import { useMutation, useQuery } from "@apollo/client/react";
import i18n from "@/shared/i18n/config";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { useAuth } from "./AuthProvider";
import { USER_PREFERENCES } from "@/lib/graphql/operations/auth/queries";
import { UPDATE_USER_PREFERENCES } from "@/lib/graphql/operations/auth/mutations";
import { ACCOUNT_PREFERENCES } from "@/lib/graphql/operations/banking/queries";
import { UPDATE_ACCOUNT_PREFERENCES } from "@/lib/graphql/operations/banking/mutations";
import type {
  UserPreferencesQuery,
} from "@/lib/graphql/operations/auth/queries.generated";
import type { AccountPreferencesQuery } from "@/lib/graphql/operations/banking/queries.generated";
import {
  Language,
  type Mutation,
  RuleApplicationMode,
  Theme,
  TransactionStatus,
  type UpdateAccountPreferencesInput,
  type UpdateUserPreferencesInput,
} from "@/lib/graphql/types/__generated__/graphql";

type UserPreferencesState = {
  language: Language;
  theme: Theme;
  blurBalances: boolean;
  firstDayOfWeek: number;
};

type AccountPreferencesState = {
  ruleApplicationMode: RuleApplicationMode;
  defaultTransactionStatus: TransactionStatus;
};

type PreferencesContextValue = {
  userPreferences: UserPreferencesState;
  accountPreferences: AccountPreferencesState;
  isLoading: boolean;
  canManageAccountPreferences: boolean;
  updateUserPreferences: (patch: UpdateUserPreferencesInput) => Promise<void>;
  updateAccountPreferences: (patch: UpdateAccountPreferencesInput) => Promise<void>;
};

const USER_PREFS_CACHE_KEY = "@invistus/userPreferences";

const defaultUserPreferences: UserPreferencesState = {
  language: Language.Portuguese,
  theme: Theme.System,
  blurBalances: false,
  firstDayOfWeek: 1,
};

const defaultAccountPreferences: AccountPreferencesState = {
  ruleApplicationMode: RuleApplicationMode.AlwaysPrompt,
  defaultTransactionStatus: TransactionStatus.Posted,
};

const PreferencesContext = React.createContext<PreferencesContextValue | null>(null);

const getSystemThemeMode = (): "light" | "dark" =>
  typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

function readCachedUserPreferences(): UserPreferencesState | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(USER_PREFS_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<UserPreferencesState>;
    if (!parsed.language || !parsed.theme || typeof parsed.blurBalances !== "boolean") return null;
    return {
      language: parsed.language,
      theme: parsed.theme,
      blurBalances: parsed.blurBalances,
      firstDayOfWeek: typeof parsed.firstDayOfWeek === "number" ? parsed.firstDayOfWeek : 1,
    };
  } catch {
    return null;
  }
}

function writeCachedUserPreferences(value: UserPreferencesState): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(USER_PREFS_CACHE_KEY, JSON.stringify(value));
}

export function PreferencesProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const { setMode } = useThemeMode();

  const [userPreferences, setUserPreferences] = React.useState<UserPreferencesState>(() => readCachedUserPreferences() ?? defaultUserPreferences);
  const [accountPreferences, setAccountPreferences] = React.useState<AccountPreferencesState>(defaultAccountPreferences);

  const [canManageAccountPreferences, setCanManageAccountPreferences] = React.useState(true);

  const userPrefsQuery = useQuery<UserPreferencesQuery>(USER_PREFERENCES, { skip: !isAuthenticated });
  const accountPrefsQuery = useQuery<AccountPreferencesQuery>(ACCOUNT_PREFERENCES, { skip: !isAuthenticated });
  const [updateUserPreferencesMutation] = useMutation<
    Pick<Mutation, "updateUserPreferences">,
    { input: UpdateUserPreferencesInput }
  >(UPDATE_USER_PREFERENCES);
  const [updateAccountPreferencesMutation] = useMutation<
    Pick<Mutation, "updateAccountPreferences">,
    { input: UpdateAccountPreferencesInput }
  >(UPDATE_ACCOUNT_PREFERENCES);

  React.useEffect(() => {
    const data = userPrefsQuery.data?.userPreferences;
    if (!data) return;
    const next: UserPreferencesState = {
      language: data.language,
      theme: data.theme,
      blurBalances: data.blurBalances,
      firstDayOfWeek: data.firstDayOfWeek,
    };
    setUserPreferences(next);
    writeCachedUserPreferences(next);
  }, [userPrefsQuery.data]);

  React.useEffect(() => {
    const data = accountPrefsQuery.data?.accountPreferences;
    if (!data) return;
    setAccountPreferences({
      ruleApplicationMode: data.ruleApplicationMode,
      defaultTransactionStatus: data.defaultTransactionStatus,
    });
  }, [accountPrefsQuery.data]);

  React.useEffect(() => {
    if (userPrefsQuery.error) {
      const fallback = readCachedUserPreferences();
      if (fallback) setUserPreferences(fallback);
    }
  }, [userPrefsQuery.error]);

  React.useEffect(() => {
    const nextLanguage = userPreferences.language === Language.English ? "en" : "pt";
    if (i18n.language !== nextLanguage) {
      void i18n.changeLanguage(nextLanguage);
    }
  }, [userPreferences.language]);

  React.useEffect(() => {
    if (userPreferences.theme === Theme.Light) {
      setMode("light");
      return;
    }
    if (userPreferences.theme === Theme.Dark) {
      setMode("dark");
      return;
    }

    const applySystemMode = () => setMode(getSystemThemeMode());
    applySystemMode();
    if (typeof window === "undefined") return;
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    mediaQuery.addEventListener("change", applySystemMode);
    return () => mediaQuery.removeEventListener("change", applySystemMode);
  }, [setMode, userPreferences.theme]);

  const updateUserPreferences = React.useCallback(
    async (patch: UpdateUserPreferencesInput) => {
      const previous = userPreferences;
      const optimistic: UserPreferencesState = {
        language: patch.language ?? previous.language,
        theme: patch.theme ?? previous.theme,
        blurBalances: patch.blurBalances ?? previous.blurBalances,
        firstDayOfWeek: patch.firstDayOfWeek ?? previous.firstDayOfWeek,
      };
      setUserPreferences(optimistic);
      writeCachedUserPreferences(optimistic);
      try {
      const result = await updateUserPreferencesMutation({ variables: { input: patch } });
        const saved = result.data?.updateUserPreferences;
        if (saved) {
          const next: UserPreferencesState = {
          language: saved.language ?? previous.language,
          theme: saved.theme ?? previous.theme,
          blurBalances: saved.blurBalances ?? previous.blurBalances,
          firstDayOfWeek: saved.firstDayOfWeek ?? previous.firstDayOfWeek,
          };
          setUserPreferences(next);
          writeCachedUserPreferences(next);
        }
      } catch (error) {
        setUserPreferences(previous);
        writeCachedUserPreferences(previous);
        throw error;
      }
    },
    [updateUserPreferencesMutation, userPreferences],
  );

  const updateAccountPreferences = React.useCallback(
    async (patch: UpdateAccountPreferencesInput) => {
      const previous = accountPreferences;
      const optimistic: AccountPreferencesState = {
        ruleApplicationMode: patch.ruleApplicationMode ?? previous.ruleApplicationMode,
        defaultTransactionStatus: patch.defaultTransactionStatus ?? previous.defaultTransactionStatus,
      };
      setAccountPreferences(optimistic);
      try {
      const result = await updateAccountPreferencesMutation({ variables: { input: patch } });
        const saved = result.data?.updateAccountPreferences;
        if (saved) {
          setCanManageAccountPreferences(true);
          setAccountPreferences({
          ruleApplicationMode: saved.ruleApplicationMode ?? previous.ruleApplicationMode,
          defaultTransactionStatus: saved.defaultTransactionStatus ?? previous.defaultTransactionStatus,
          });
        }
      } catch (error) {
        setAccountPreferences(previous);
        const serialized = JSON.stringify(error);
        if (serialized.includes("ACCOUNT_PREFERENCES_OWNER_REQUIRED")) {
          setCanManageAccountPreferences(false);
        }
        throw error;
      }
    },
    [accountPreferences, updateAccountPreferencesMutation],
  );

  const value = React.useMemo<PreferencesContextValue>(
    () => ({
      userPreferences,
      accountPreferences,
      isLoading: (userPrefsQuery.loading || accountPrefsQuery.loading) && isAuthenticated,
      canManageAccountPreferences,
      updateUserPreferences,
      updateAccountPreferences,
    }),
    [accountPreferences, accountPrefsQuery.loading, canManageAccountPreferences, isAuthenticated, updateAccountPreferences, updateUserPreferences, userPreferences, userPrefsQuery.loading],
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}

export function usePreferences(): PreferencesContextValue {
  const context = React.useContext(PreferencesContext);
  if (!context) {
    throw new Error("usePreferences must be used within PreferencesProvider");
  }
  return context;
}

export function useOptionalPreferences(): PreferencesContextValue | null {
  return React.useContext(PreferencesContext);
}
