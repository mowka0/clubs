export interface Club {
  id: string
  ownerId: string
  name: string
  description: string | null
  city: string | null
  category: string
  accessType: 'open' | 'closed' | 'private'
  memberLimit: number
  subscriptionPrice: number
  avatarUrl: string | null
  coverUrl: string | null
  rules: string | null
  applicationQuestion: string | null
  telegramGroupId: number | null
  activityRating: number
  confirmedCount: number
  isActive: boolean
  createdAt: string
  updatedAt: string
  promoTags: string[]
  goingCount: number
}

export interface ClubMember {
  userId: string
  username: string | null
  firstName: string | null
  lastName: string | null
  avatarUrl: string | null
  role: string
  joinedAt: string
  reliabilityIndex: number
}

export interface PagedClubsResponse {
  content: Club[]
  page: number
  size: number
  totalElements: number
}

export interface ClubFilters {
  city?: string
  category?: string
  accessType?: string
  priceMin?: number
  priceMax?: number
  search?: string
  sort?: 'relevance' | 'newest' | 'price_asc' | 'price_desc'
  page?: number
  size?: number
}

export const CLUB_CATEGORIES = [
  { value: 'sport', label: 'Спорт' },
  { value: 'education', label: 'Образование' },
  { value: 'business', label: 'Бизнес' },
  { value: 'hobby', label: 'Хобби' },
  { value: 'social', label: 'Общение' },
  { value: 'tech', label: 'Технологии' },
  { value: 'art', label: 'Искусство' },
  { value: 'other', label: 'Другое' },
] as const

export const CATEGORY_LABELS: Record<string, string> = Object.fromEntries(
  CLUB_CATEGORIES.map(({ value, label }) => [value, label]),
)

export const ACCESS_TYPE_LABELS: Record<string, string> = {
  open: 'Открытый',
  closed: 'Закрытый',
  private: 'Приватный',
}
