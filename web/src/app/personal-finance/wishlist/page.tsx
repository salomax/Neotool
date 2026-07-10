import { redirect } from 'next/navigation';

export default function LegacyWishlistPage() {
  redirect('/personal-finance/purchase-wishlist');
}
