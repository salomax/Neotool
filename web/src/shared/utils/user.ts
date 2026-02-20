export const getInitials = (user: { displayName?: string | null; email: string } | null) => {
  if (!user) return "?";
  if (user.displayName) {
    const names = user.displayName.trim().split(/\s+/);
    if (names.length >= 2) {
      const first = names[0]?.[0];
      const last = names[names.length - 1]?.[0];
      if (first && last) {
        return `${first}${last}`.toUpperCase();
      }
    }
    const firstChar = user.displayName.trim()[0];
    if (firstChar) {
      return firstChar.toUpperCase();
    }
  }
  const emailChar = user.email?.[0];
  return emailChar ? emailChar.toUpperCase() : "?";
};
