export default function SettingsLayout({
  children,
}: {
  children: React.ReactNode
}) {
  // Nested layouts should not include <html> or <body> tags
  // Only the root layout should have those
  return <>{children}</>;
}
