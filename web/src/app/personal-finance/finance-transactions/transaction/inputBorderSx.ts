export function inputBorderSx(base: object, hasError: boolean): object {
  if (!hasError) return base;
  return {
    ...base,
    borderBottomColor: 'error.main',
    borderBottomWidth: 2,
    '&.Mui-focused': {
      borderBottomColor: 'error.main',
    },
  };
}
