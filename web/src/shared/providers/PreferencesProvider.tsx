"use client";

import * as React from "react";
import { useMutation, useQuery } from "@apollo/client/react";
import i18n from "@/shared/i18n/config";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { useAuth } from "./AuthProvider";
import { USER_PREFERENCES } from "@/lib/graphql/operations/auth/queries";
import { UPDATE_USER_PREFERENCES } from "@/lib/graphql/operations/auth/mutations";
import { ACCOUNT_PREFERENCES, BANKING_USER_PREFERENCES } from "@/lib/graphql/operations/banking/queries";
import { UPDATE_ACCOUNT_PREFERENCES, UPDATE_BANKING_USER_PREFERENCES } from "@/lib/graphql/operations/banking/mutations";
import type { AccountPreferencesQuery, BankingUserPreferencesQuery } from "@/lib/graphql/operations/banking/queries.generated";
import {
  Language,
  RuleCreationMode,
  Theme,
  TransactionStatus,
  type Mutation,
  type UpdateAccountPreferencesInput,
  type UpdateBankingUserPreferencesInput,
  type UpdateUserPreferencesInput,
} from "@/lib/graphql/types/__generated__/graphql";

type UserPreferencesState = {
  language: Language;
  theme: Theme;
  blurBalances: boolean;
  firstDayOfWeek: number;
  keepMeConnected: boolean;
};

type BankingUserPreferencesState = {
  ruleCreationMode: RuleCreationMode;
};

type AccountPreferencesState = {
  defaultTransactionStatus: TransactionStatus;
};

type PreferencesContextValue = {
  userPreferences: UserPreferencesState;
  bankingUserPreferences: BankingUserPreferencesState;
  accountPreferences: AccountPreferencesState;
  isLoading: boolean;
  canManageAccountPreferences: boolean;
  updateUserPreferences: (patch: UserPreferencesPatch) => Promise<void>;
  updateBankingUserPreferences: (patch: UpdateBankingUserPreferencesInput) => Promise<void>;
  updateAccountPreferences: (patch: UpdateAccountPreferencesInput) => Promise<void>;
};

type UserPreferencesPatch = UpdateUserPreferencesInput & {
  keepMeConnected?: boolean;
};

type UserPreferencesQueryData = {
  userPreferences: {
    language: Language;
    theme: Theme;
    blurBalances: boolean;
    firstDayOfWeek: number;
    keepMeConnected?: boolean | null;
  };
};

type UpdateUserPreferencesMutationData = {
  updateUserPreferences: {
    language: Language;
    theme: Theme;
    blurBalances: boolean;
    firstDayOfWeek: number;
    keepMeConnected?: boolean | null;
  };
};

const USER_PREFS_CACHE_KEY = "@invistus/userPreferences";
const BANKING_USER_PREFS_CACHE_KEY = "@invistus/bankingUserPreferences";

const defaultUserPreferences: UserPreferencesState = {
  language: Language.Portuguese,
  theme: Theme.System,
  blurBalances: false,
  firstDayOfWeek: 1,
  keepMeConnected: true,
};

const defaultBankingUserPreferences: BankingUserPreferencesState = {
  ruleCreationMode: RuleCreationMode.AlwaysPrompt,
};

const defaultAccountPreferences: AccountPreferencesState = {
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
      keepMeConnected: typeof parsed.keepMeConnected === "boolean" ? parsed.keepMeConnected : true,
    };
  } catch {
    return null;
  }
}

function writeCachedUserPreferences(value: UserPreferencesState): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(USER_PREFS_CACHE_KEY, JSON.stringify(value));
}

function readCachedBankingUserPreferences(): BankingUserPreferencesState | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(BANKING_USER_PREFS_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<BankingUserPreferencesState>;
    if (!parsed.ruleCreationMode) return null;
    return {
      ruleCreationMode: parsed.ruleCreationMode,
    };
  } catch {
    return null;
  }
}

function writeCachedBankingUserPreferences(value: BankingUserPreferencesState): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(BANKING_USER_PREFS_CACHE_KEY, JSON.stringify(value));
}

export function PreferencesProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const { setMode } = useThemeMode();

  const [userPreferences, setUserPreferences] = React.useState<UserPreferencesState>(() => readCachedUserPreferences() ?? defaultUserPreferences);
  const [bankingUserPreferences, setBankingUserPreferences] = React.useState<BankingUserPreferencesState>(() => readCachedBankingUserPreferences() ?? defaultBankingUserPreferences);
  const [accountPreferences, setAccountPreferences] = React.useState<AccountPreferencesState>(defaultAccountPreferences);

  const [canManageAccountPreferences, setCanManageAccountPreferences] = React.useState(true);

  const userPrefsQuery = useQuery<UserPreferencesQueryData>(USER_PREFERENCES, { skip: !isAuthenticated });
  const bankingUserPrefsQuery = useQuery<BankingUserPreferencesQuery>(BANKING_USER_PREFERENCES, { skip: !isAuthenticated });
  const accountPrefsQuery = useQuery<AccountPreferencesQuery>(ACCOUNT_PREFERENCES, { skip: !isAuthenticated });
  const [updateUserPreferencesMutation] = useMutation<
    UpdateUserPreferencesMutationData,
    { input: UserPreferencesPatch }
  >(UPDATE_USER_PREFERENCES);
  const [updateBankingUserPreferencesMutation] = useMutation<
    Pick<Mutation, "updateBankingUserPreferences">,
    { input: UpdateBankingUserPreferencesInput }
  >(UPDATE_BANKING_USER_PREFERENCES);
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
      keepMeConnected: data.keepMeConnected ?? true,
    };
    setUserPreferences(next);
    writeCachedUserPreferences(next);
  }, [userPrefsQuery.data]);

  React.useEffect(() => {
    const data = bankingUserPrefsQuery.data?.bankingUserPreferences;
    if (!data) return;
    const next: BankingUserPreferencesState = {
      ruleCreationMode: data.ruleCreationMode,
    };
    setBankingUserPreferences(next);
    writeCachedBankingUserPreferences(next);
  }, [bankingUserPrefsQuery.data]);

  React.useEffect(() => {
    const data = accountPrefsQuery.data?.accountPreferences;
    if (!data) return;
    setAccountPreferences({
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
    if (bankingUserPrefsQuery.error) {
      const fallback = readCachedBankingUserPreferences();
      if (fallback) setBankingUserPreferences(fallback);
    }
  }, [bankingUserPrefsQuery.error]);

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
    async (patch: UserPreferencesPatch) => {
      const previous = userPreferences;
      const optimistic: UserPreferencesState = {
        language: patch.language ?? previous.language,
        theme: patch.theme ?? previous.theme,
        blurBalances: patch.blurBalances ?? previous.blurBalances,
        firstDayOfWeek: patch.firstDayOfWeek ?? previous.firstDayOfWeek,
        keepMeConnected: patch.keepMeConnected ?? previous.keepMeConnected,
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
            keepMeConnected: saved.keepMeConnected ?? previous.keepMeConnected,
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

  const updateBankingUserPreferences = React.useCallback(
    async (patch: UpdateBankingUserPreferencesInput) => {
      const previous = bankingUserPreferences;
      const optimistic: BankingUserPreferencesState = {
        ruleCreationMode: patch.ruleCreationMode ?? previous.ruleCreationMode,
      };
      setBankingUserPreferences(optimistic);
      writeCachedBankingUserPreferences(optimistic);
      try {
        const result = await updateBankingUserPreferencesMutation({ variables: { input: patch } });
        const saved = result.data?.updateBankingUserPreferences;
        if (saved) {
          const next: BankingUserPreferencesState = {
            ruleCreationMode: saved.ruleCreationMode ?? previous.ruleCreationMode,
          };
          setBankingUserPreferences(next);
          writeCachedBankingUserPreferences(next);
        }
      } catch (error) {
        setBankingUserPreferences(previous);
        writeCachedBankingUserPreferences(previous);
        throw error;
      }
    },
    [bankingUserPreferences, updateBankingUserPreferencesMutation],
  );

  const updateAccountPreferences = React.useCallback(
    async (patch: UpdateAccountPreferencesInput) => {
      const previous = accountPreferences;
      const optimistic: AccountPreferencesState = {
        defaultTransactionStatus: patch.defaultTransactionStatus ?? previous.defaultTransactionStatus,
      };
      setAccountPreferences(optimistic);
      try {
        const result = await updateAccountPreferencesMutation({ variables: { input: patch } });
        const saved = result.data?.updateAccountPreferences;
        if (saved) {
          setCanManageAccountPreferences(true);
          setAccountPreferences({
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
      bankingUserPreferences,
      accountPreferences,
      isLoading: (userPrefsQuery.loading || bankingUserPrefsQuery.loading || accountPrefsQuery.loading) && isAuthenticated,
      canManageAccountPreferences,
      updateUserPreferences,
      updateBankingUserPreferences,
      updateAccountPreferences,
    }),
    [accountPreferences, accountPrefsQuery.loading, bankingUserPreferences, bankingUserPrefsQuery.loading, canManageAccountPreferences, isAuthenticated, updateAccountPreferences, updateBankingUserPreferences, updateUserPreferences, userPreferences, userPrefsQuery.loading],
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
