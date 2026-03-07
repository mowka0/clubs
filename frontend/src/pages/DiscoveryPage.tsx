import { useState, useEffect, useRef, useCallback } from 'react'
import { clubsApi } from '../api/clubs'
import { membershipApi } from '../api/membership'
import { ClubCard, ClubCardSkeleton } from '../components/ClubCard'
import { useClubsStore } from '../store/clubsStore'
import type { ClubFilters } from '../types/club'
import { CLUB_CATEGORIES } from '../types/club'

const PAGE_SIZE = 10

interface FilterState {
  search: string
  category: string
  city: string
  priceMax: number | null
  sort: 'relevance' | 'newest' | 'price_asc' | 'price_desc'
}

const DEFAULT_FILTERS: FilterState = {
  search: '',
  category: '',
  city: '',
  priceMax: null,
  sort: 'relevance',
}

function buildApiFilters(fs: FilterState): ClubFilters {
  const f: ClubFilters = { sort: fs.sort, size: PAGE_SIZE }
  if (fs.search.trim()) f.search = fs.search.trim()
  if (fs.category) f.category = fs.category
  if (fs.city.trim()) f.city = fs.city.trim()
  if (fs.priceMax != null) f.priceMax = fs.priceMax
  return f
}

export function DiscoveryPage() {
  const { clubs, isLoading, hasMore, setClubs, appendClubs, setLoading, setHasMore, reset } = useClubsStore()

  const [filterState, setFilterState] = useState<FilterState>(DEFAULT_FILTERS)
  const [detectedCity, setDetectedCity] = useState<string | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  const [cityInput, setCityInput] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pendingAppsCount, setPendingAppsCount] = useState(0)

  const pageRef = useRef(0)
  const activeFiltersRef = useRef<ClubFilters>({})
  const bottomRef = useRef<HTMLDivElement>(null)
  const observerRef = useRef<IntersectionObserver | null>(null)

  // Detect city on mount
  useEffect(() => {
    clubsApi.getGeoCity()
      .then((res) => { if (res.city) setDetectedCity(res.city) })
      .catch(() => {/* ignore */})
  }, [])

  // Load pending applications count
  useEffect(() => {
    membershipApi.getMyApplications()
      .then((apps) => setPendingAppsCount(apps.filter((a) => a.status === 'pending').length))
      .catch(() => {/* ignore */})
  }, [])

  const fetchClubs = useCallback(async (apiFilters: ClubFilters, page: number, append: boolean) => {
    setLoading(true)
    setError(null)
    try {
      const res = await clubsApi.getClubs({ ...apiFilters, page, size: PAGE_SIZE })
      if (append) {
        appendClubs(res.content)
      } else {
        setClubs(res.content)
      }
      const totalPages = Math.ceil(res.totalElements / PAGE_SIZE)
      setHasMore(page + 1 < totalPages)
      pageRef.current = page
    } catch {
      setError('Не удалось загрузить клубы. Попробуйте ещё раз.')
    } finally {
      setLoading(false)
    }
  }, [setClubs, appendClubs, setLoading, setHasMore])

  const applyAndFetch = useCallback((fs: FilterState, city?: string) => {
    const apiFilters = buildApiFilters(fs)
    const effectiveCity = city ?? detectedCity
    if (!apiFilters.city && effectiveCity) apiFilters.city = effectiveCity
    activeFiltersRef.current = apiFilters
    pageRef.current = 0
    reset()
    fetchClubs(apiFilters, 0, false)
  }, [detectedCity, reset, fetchClubs])

  // Initial load
  useEffect(() => {
    applyAndFetch(DEFAULT_FILTERS)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Setup infinite scroll
  useEffect(() => {
    if (observerRef.current) observerRef.current.disconnect()
    observerRef.current = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !isLoading && hasMore) {
        const nextPage = pageRef.current + 1
        fetchClubs(activeFiltersRef.current, nextPage, true)
      }
    }, { rootMargin: '100px' })
    if (bottomRef.current) observerRef.current.observe(bottomRef.current)
    return () => observerRef.current?.disconnect()
  }, [isLoading, hasMore, fetchClubs])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const newFs = { ...filterState, search: searchInput, city: cityInput }
    setFilterState(newFs)
    applyAndFetch(newFs)
  }

  const handleCategoryChange = (cat: string) => {
    const newFs = { ...filterState, category: cat }
    setFilterState(newFs)
    applyAndFetch(newFs)
  }

  const handleSortChange = (sort: FilterState['sort']) => {
    const newFs = { ...filterState, sort }
    setFilterState(newFs)
    applyAndFetch(newFs)
  }

  const handleApplyAdvanced = (priceMax: number | null) => {
    const newFs = { ...filterState, priceMax, search: searchInput, city: cityInput }
    setFilterState(newFs)
    applyAndFetch(newFs)
  }

  const handleReset = () => {
    setSearchInput('')
    setCityInput('')
    setFilterState(DEFAULT_FILTERS)
    applyAndFetch(DEFAULT_FILTERS)
  }

  return (
    <div style={{ padding: '0 12px', paddingBottom: 80 }}>
      {/* Header */}
      <div style={{ padding: '16px 0 8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          Клубы
        </h1>
        {pendingAppsCount > 0 && (
          <div style={{
            background: 'var(--tg-theme-button-color, #2196F3)',
            color: 'var(--tg-theme-button-text-color, #fff)',
            borderRadius: 10,
            padding: '4px 10px',
            fontSize: 13,
            fontWeight: 600,
          }}>
            {pendingAppsCount} {pendingAppsCount === 1 ? 'заявка ожидает' : 'заявки ожидают'}
          </div>
        )}
      </div>

      {/* Search bar */}
      <form onSubmit={handleSearch} style={{ marginBottom: 12 }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            type="text"
            placeholder="Поиск по названию..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            style={{
              flex: 1,
              padding: '10px 14px',
              borderRadius: 12,
              border: '1px solid var(--tg-theme-hint-color, #ddd)',
              background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
              color: 'var(--tg-theme-text-color, #000)',
              fontSize: 14,
              outline: 'none',
            }}
          />
          <button
            type="submit"
            style={{
              padding: '10px 16px',
              borderRadius: 12,
              border: 'none',
              background: 'var(--tg-theme-button-color, #2196F3)',
              color: 'var(--tg-theme-button-text-color, #fff)',
              fontSize: 14,
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            Найти
          </button>
        </div>
      </form>

      {/* City filter */}
      <div style={{ marginBottom: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
        <span style={{ fontSize: 13, color: 'var(--tg-theme-hint-color, #888)', whiteSpace: 'nowrap' }}>Город:</span>
        <input
          type="text"
          placeholder={detectedCity ?? 'Все города'}
          value={cityInput}
          onChange={(e) => setCityInput(e.target.value)}
          style={{
            flex: 1,
            padding: '7px 12px',
            borderRadius: 10,
            border: '1px solid var(--tg-theme-hint-color, #ddd)',
            background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
            color: 'var(--tg-theme-text-color, #000)',
            fontSize: 13,
            outline: 'none',
          }}
        />
        {(cityInput || detectedCity) && (
          <button
            onClick={() => { setCityInput(''); setDetectedCity(null) }}
            style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 16, padding: 4, color: 'var(--tg-theme-hint-color, #888)' }}
          >
            ✕
          </button>
        )}
      </div>

      {/* Category chips */}
      <div style={{ display: 'flex', gap: 8, overflowX: 'auto', paddingBottom: 4, marginBottom: 8, scrollbarWidth: 'none' }}>
        <button
          onClick={() => handleCategoryChange('')}
          style={{
            whiteSpace: 'nowrap',
            padding: '6px 14px',
            borderRadius: 20,
            border: 'none',
            background: !filterState.category ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
            color: !filterState.category ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
            cursor: 'pointer',
            fontSize: 13,
            fontWeight: 500,
          }}
        >
          Все
        </button>
        {CLUB_CATEGORIES.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => handleCategoryChange(value)}
            style={{
              whiteSpace: 'nowrap',
              padding: '6px 14px',
              borderRadius: 20,
              border: 'none',
              background: filterState.category === value ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
              color: filterState.category === value ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Sort + Advanced toggle */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <select
          value={filterState.sort}
          onChange={(e) => handleSortChange(e.target.value as FilterState['sort'])}
          style={{
            padding: '6px 10px',
            borderRadius: 10,
            border: '1px solid var(--tg-theme-hint-color, #ddd)',
            background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
            color: 'var(--tg-theme-text-color, #000)',
            fontSize: 13,
            outline: 'none',
          }}
        >
          <option value="relevance">По релевантности</option>
          <option value="newest">Новые сначала</option>
          <option value="price_asc">Цена ↑</option>
          <option value="price_desc">Цена ↓</option>
        </select>

        <button
          onClick={() => setShowAdvanced((v) => !v)}
          style={{
            background: 'none',
            border: '1px solid var(--tg-theme-hint-color, #ddd)',
            borderRadius: 10,
            padding: '6px 12px',
            fontSize: 13,
            cursor: 'pointer',
            color: 'var(--tg-theme-text-color, #000)',
          }}
        >
          {showAdvanced ? 'Скрыть ↑' : 'Ещё фильтры ↓'}
        </button>
      </div>

      {/* Advanced filters */}
      {showAdvanced && (
        <AdvancedFilters
          initialPriceMax={filterState.priceMax}
          onApply={handleApplyAdvanced}
        />
      )}

      {/* Error state */}
      {error && (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', marginBottom: 12 }}>{error}</p>
          <button
            onClick={() => applyAndFetch(filterState)}
            style={{
              padding: '10px 24px',
              borderRadius: 12,
              border: 'none',
              background: 'var(--tg-theme-button-color, #2196F3)',
              color: 'var(--tg-theme-button-text-color, #fff)',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            Повторить
          </button>
        </div>
      )}

      {/* Loading skeletons (initial load) */}
      {isLoading && clubs.length === 0 && (
        <>
          <ClubCardSkeleton />
          <ClubCardSkeleton />
          <ClubCardSkeleton />
        </>
      )}

      {/* Clubs list */}
      {clubs.map((club) => (
        <ClubCard key={club.id} club={club} />
      ))}

      {/* Empty state */}
      {!isLoading && !error && clubs.length === 0 && (
        <div style={{ textAlign: 'center', padding: '48px 24px' }}>
          <p style={{ fontSize: 40, margin: '0 0 12px' }}>🔍</p>
          <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 8 }}>
            Клубов не найдено
          </p>
          <p style={{ fontSize: 14, color: 'var(--tg-theme-hint-color, #888)', marginBottom: 20 }}>
            Попробуйте изменить фильтры или поисковый запрос
          </p>
          <button
            onClick={handleReset}
            style={{
              padding: '10px 24px',
              borderRadius: 12,
              border: 'none',
              background: 'var(--tg-theme-button-color, #2196F3)',
              color: 'var(--tg-theme-button-text-color, #fff)',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            Сбросить фильтры
          </button>
        </div>
      )}

      {/* Infinite scroll trigger + loading more */}
      <div ref={bottomRef} style={{ height: 1 }} />
      {isLoading && clubs.length > 0 && (
        <div style={{ textAlign: 'center', padding: 16, color: 'var(--tg-theme-hint-color, #888)', fontSize: 13 }}>
          Загрузка...
        </div>
      )}
    </div>
  )
}

function AdvancedFilters({ initialPriceMax, onApply }: { initialPriceMax: number | null; onApply: (priceMax: number | null) => void }) {
  const [priceMax, setPriceMax] = useState(initialPriceMax ?? 1000)

  return (
    <div
      style={{
        background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
        borderRadius: 12,
        padding: 12,
        marginBottom: 12,
      }}
    >
      <div style={{ marginBottom: 10 }}>
        <label style={{ fontSize: 13, color: 'var(--tg-theme-hint-color, #888)', display: 'block', marginBottom: 4 }}>
          Макс. цена: {priceMax >= 1000 ? 'Любая' : `${priceMax} ⭐/мес`}
        </label>
        <input
          type="range"
          min={0}
          max={1000}
          step={50}
          value={priceMax}
          onChange={(e) => setPriceMax(Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>
      <button
        onClick={() => onApply(priceMax >= 1000 ? null : priceMax)}
        style={{
          width: '100%',
          padding: '10px',
          borderRadius: 10,
          border: 'none',
          background: 'var(--tg-theme-button-color, #2196F3)',
          color: 'var(--tg-theme-button-text-color, #fff)',
          fontSize: 14,
          cursor: 'pointer',
          fontWeight: 600,
        }}
      >
        Применить
      </button>
    </div>
  )
}
