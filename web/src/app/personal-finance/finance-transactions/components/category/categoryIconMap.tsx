'use client';

import React from 'react';
import type { SvgIconProps } from '@mui/material/SvgIcon';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import PaymentsIcon from '@mui/icons-material/Payments';
import WorkIcon from '@mui/icons-material/Work';
import ElderlyIcon from '@mui/icons-material/Elderly';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CallReceivedIcon from '@mui/icons-material/CallReceived';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import RamenDiningIcon from '@mui/icons-material/RamenDining';
import DeliveryDiningIcon from '@mui/icons-material/DeliveryDining';
import LocalCafeIcon from '@mui/icons-material/LocalCafe';
import HomeIcon from '@mui/icons-material/Home';
import KeyIcon from '@mui/icons-material/Key';
import ApartmentIcon from '@mui/icons-material/Apartment';
import BoltIcon from '@mui/icons-material/Bolt';
import WaterDropIcon from '@mui/icons-material/WaterDrop';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation';
import DirectionsBusIcon from '@mui/icons-material/DirectionsBus';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import MedicationIcon from '@mui/icons-material/Medication';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import HealthAndSafetyIcon from '@mui/icons-material/HealthAndSafety';
import SchoolIcon from '@mui/icons-material/School';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import EditIcon from '@mui/icons-material/Edit';
import CelebrationIcon from '@mui/icons-material/Celebration';
import MovieIcon from '@mui/icons-material/Movie';
import FitnessCenterIcon from '@mui/icons-material/FitnessCenter';
import FlightIcon from '@mui/icons-material/Flight';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import CheckroomIcon from '@mui/icons-material/Checkroom';
import DevicesIcon from '@mui/icons-material/Devices';
import ChairIcon from '@mui/icons-material/Chair';
import LiveTvIcon from '@mui/icons-material/LiveTv';
import HeadphonesIcon from '@mui/icons-material/Headphones';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import CellTowerIcon from '@mui/icons-material/CellTower';
import WifiIcon from '@mui/icons-material/Wifi';
import PhoneIphoneIcon from '@mui/icons-material/PhoneIphone';
import ShieldIcon from '@mui/icons-material/Shield';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import BarChartIcon from '@mui/icons-material/BarChart';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import FavoriteIcon from '@mui/icons-material/Favorite';
import CallMadeIcon from '@mui/icons-material/CallMade';
import SavingsIcon from '@mui/icons-material/Savings';
import LocalBarIcon from '@mui/icons-material/LocalBar';
import CakeIcon from '@mui/icons-material/Cake';
import LunchDiningIcon from '@mui/icons-material/LunchDining';
import StoreIcon from '@mui/icons-material/Store';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import RedeemIcon from '@mui/icons-material/Redeem';
import DirectionsBoatIcon from '@mui/icons-material/DirectionsBoat';
import TrainIcon from '@mui/icons-material/Train';
import TwoWheelerIcon from '@mui/icons-material/TwoWheeler';
import DirectionsSubwayIcon from '@mui/icons-material/DirectionsSubway';
import BuildIcon from '@mui/icons-material/Build';
import HandymanIcon from '@mui/icons-material/Handyman';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import PetsIcon from '@mui/icons-material/Pets';
import ChildCareIcon from '@mui/icons-material/ChildCare';
import SpaIcon from '@mui/icons-material/Spa';
import LuggageIcon from '@mui/icons-material/Luggage';
import BeachAccessIcon from '@mui/icons-material/BeachAccess';
import BusinessCenterIcon from '@mui/icons-material/BusinessCenter';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import CurrencyExchangeIcon from '@mui/icons-material/CurrencyExchange';
import PieChartIcon from '@mui/icons-material/PieChart';
import SportsEsportsIcon from '@mui/icons-material/SportsEsports';
import MusicNoteIcon from '@mui/icons-material/MusicNote';
import BookIcon from '@mui/icons-material/Book';
import ScienceIcon from '@mui/icons-material/Science';
import AgricultureIcon from '@mui/icons-material/Agriculture';
import LocalAtmIcon from '@mui/icons-material/LocalAtm';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import RestaurantMenuIcon from '@mui/icons-material/RestaurantMenu';
import NightlifeIcon from '@mui/icons-material/Nightlife';
import ChildFriendlyIcon from '@mui/icons-material/ChildFriendly';
import SelfImprovementIcon from '@mui/icons-material/SelfImprovement';
import VolunteerActivismIcon from '@mui/icons-material/VolunteerActivism';
import ForestIcon from '@mui/icons-material/Forest';
import ElectricBoltIcon from '@mui/icons-material/ElectricBolt';

export const categoryIconMap: Record<string, React.ComponentType<SvgIconProps>> = {
  payments: PaymentsIcon,
  work: WorkIcon,
  elderly: ElderlyIcon,
  trending_up: TrendingUpIcon,
  call_received: CallReceivedIcon,
  restaurant: RestaurantIcon,
  shopping_cart: ShoppingCartIcon,
  ramen_dining: RamenDiningIcon,
  delivery_dining: DeliveryDiningIcon,
  local_cafe: LocalCafeIcon,
  home: HomeIcon,
  key: KeyIcon,
  apartment: ApartmentIcon,
  bolt: BoltIcon,
  water_drop: WaterDropIcon,
  local_fire_department: LocalFireDepartmentIcon,
  directions_car: DirectionsCarIcon,
  local_gas_station: LocalGasStationIcon,
  directions_bus: DirectionsBusIcon,
  smartphone: SmartphoneIcon,
  local_hospital: LocalHospitalIcon,
  medication: MedicationIcon,
  medical_services: MedicalServicesIcon,
  health_and_safety: HealthAndSafetyIcon,
  school: SchoolIcon,
  account_balance: AccountBalanceIcon,
  menu_book: MenuBookIcon,
  edit: EditIcon,
  celebration: CelebrationIcon,
  movie: MovieIcon,
  fitness_center: FitnessCenterIcon,
  flight: FlightIcon,
  shopping_bag: ShoppingBagIcon,
  checkroom: CheckroomIcon,
  devices: DevicesIcon,
  chair: ChairIcon,
  live_tv: LiveTvIcon,
  headphones: HeadphonesIcon,
  autorenew: AutorenewIcon,
  cell_tower: CellTowerIcon,
  wifi: WifiIcon,
  phone_iphone: PhoneIphoneIcon,
  shield: ShieldIcon,
  receipt_long: ReceiptLongIcon,
  bar_chart: BarChartIcon,
  account_balance_wallet: AccountBalanceWalletIcon,
  credit_card: CreditCardIcon,
  favorite: FavoriteIcon,
  call_made: CallMadeIcon,
  savings: SavingsIcon,
  local_bar: LocalBarIcon,
  cake: CakeIcon,
  lunch_dining: LunchDiningIcon,
  store: StoreIcon,
  local_offer: LocalOfferIcon,
  redeem: RedeemIcon,
  card_giftcard: CardGiftcardIcon,
  restaurant_menu: RestaurantMenuIcon,
  nightlife: NightlifeIcon,
  directions_boat: DirectionsBoatIcon,
  train: TrainIcon,
  two_wheeler: TwoWheelerIcon,
  directions_subway: DirectionsSubwayIcon,
  build: BuildIcon,
  handyman: HandymanIcon,
  cleaning_services: CleaningServicesIcon,
  pets: PetsIcon,
  child_care: ChildCareIcon,
  child_friendly: ChildFriendlyIcon,
  spa: SpaIcon,
  self_improvement: SelfImprovementIcon,
  volunteer_activism: VolunteerActivismIcon,
  luggage: LuggageIcon,
  beach_access: BeachAccessIcon,
  forest: ForestIcon,
  business_center: BusinessCenterIcon,
  attach_money: AttachMoneyIcon,
  currency_exchange: CurrencyExchangeIcon,
  pie_chart: PieChartIcon,
  local_atm: LocalAtmIcon,
  sports_esports: SportsEsportsIcon,
  music_note: MusicNoteIcon,
  book: BookIcon,
  science: ScienceIcon,
  agriculture: AgricultureIcon,
  electric_bolt: ElectricBoltIcon,
  help_outline: HelpOutlineIcon,
};

export const CATEGORY_ICON_NAMES = Object.keys(categoryIconMap) as string[];

/** Color groups: each group has light + dark for clear contrast. Max 14 swatches. */
export const CATEGORY_COLOR_GROUPS: ReadonlyArray<{ colors: string[] }> = [
  { colors: ['#42a5f5', '#0d47a1'] },       // Blue: light, dark
  { colors: ['#66bb6a', '#1b5e20'] },       // Green: light, dark
  { colors: ['#26a69a', '#00695c'] },       // Teal: light, dark
  { colors: ['#ffb74d', '#e65100'] },       // Orange: light, dark
  { colors: ['#ec407a', '#b71c1c'] },       // Red/Pink: light, dark
  { colors: ['#7e57c2', '#4a148c'] },       // Purple: light, dark
  { colors: ['#78909c', '#37474f'] },       // Neutral: light grey, dark slate
  { colors: ['#a1887f', '#5d4037'] },      // Brown: light, dark
];

/** Flat list of all palette colors (for defaults and backward compatibility). */
export const CATEGORY_COLOR_PALETTE: string[] = CATEGORY_COLOR_GROUPS.flatMap((g) => g.colors);
