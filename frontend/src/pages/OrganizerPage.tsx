import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { clubsApi, type CreateClubRequest } from '../api/clubs'
import type { Club } from '../types/club'
import { CLUB_CATEGORIES } from '../types/club'
import { OrganizerClubManage } from './OrganizerClubManage'

// ─── Form state ────────────────────────────────────────────────────────────────

interface FormState {
  name: string
  city: string
  category: string
  accessType: 'open' | 'closed' | 'private'
  memberLimit: number
  subscriptionPrice: number
  description: string
  rules: string
  applicationQuestion: string
  avatarBase64: string | null
  avatarPreview: string | null
}

const INITIAL_FORM: FormState = {
  name: '',
  city: '',
  category: 'sport',
  accessType: 'open',
  memberLimit: 30,
  subscriptionPrice: 100,
  description: '',
  rules: '',
  applicationQuestion: '',
  avatarBase64: null,
  avatarPreview: null,
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function calcRevenue(price: number, limit: number) {
  const gross = price * limit
  return { organizer: Math.round(gross * 0.8), platform: Math.round(gross * 0.2) }
}

function ClubSkeleton() {
  return (
    <div style={{ padding: '16px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '12px' }}>
      <div style={{ height: '18px', width: '60%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.4, marginBottom: '8px' }} />
      <div style={{ height: '13px', width: '35%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
    </div>
  )
}

// ─── Step indicators ──────────────────────────────────────────────────────────

function StepIndicator({ current, total }: { current: number; total: number }) {
  return (
    <div style={{ display: 'flex', gap: '6px', justifyContent: 'center', margin: '0 0 24px' }}>
      {Array.from({ length: total }, (_, i) => (
        <div
          key={i}
          style={{
            height: '4px',
            borderRadius: '2px',
            flex: 1,
            background: i < current
              ? 'var(--tg-theme-button-color, #2196F3)'
              : i === current
              ? 'var(--tg-theme-button-color, #2196F3)'
              : 'var(--tg-theme-hint-color, #ccc)',
            opacity: i <= current ? 1 : 0.3,
          }}
        />
      ))}
    </div>
  )
}

// ─── Form field components ────────────────────────────────────────────────────

function FieldLabel({ children, required }: { children: React.ReactNode; required?: boolean }) {
  return (
    <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '6px', fontWeight: 500 }}>
      {children}
      {required && <span style={{ color: '#f44336', marginLeft: '2px' }}>*</span>}
    </div>
  )
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <div style={{ fontSize: '12px', color: '#f44336', marginTop: '4px' }}>{msg}</div>
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '12px 14px',
  borderRadius: '10px',
  border: '1.5px solid var(--tg-theme-secondary-bg-color, #e0e0e0)',
  background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
  color: 'var(--tg-theme-text-color, #000)',
  fontSize: '15px',
  outline: 'none',
  boxSizing: 'border-box',
}

const selectStyle: React.CSSProperties = {
  ...inputStyle,
  appearance: 'none',
  cursor: 'pointer',
}

// ─── Step 1: Basic Info ────────────────────────────────────────────────────────

interface Step1Errors {
  name?: string
  city?: string
}

function Step1({
  form,
  setForm,
  errors,
}: {
  form: FormState
  setForm: (f: Partial<FormState>) => void
  errors: Step1Errors
}) {
  return (
    <div>
      <h2 style={{ margin: '0 0 4px', fontSize: '20px' }}>Основная информация</h2>
      <p style={{ margin: '0 0 24px', fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)' }}>
        Шаг 1 из 3
      </p>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Название клуба</FieldLabel>
        <input
          style={{ ...inputStyle, borderColor: errors.name ? '#f44336' : undefined }}
          placeholder="Например: Бегуны Москвы"
          value={form.name}
          maxLength={60}
          onChange={e => setForm({ name: e.target.value })}
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
          {errors.name ? <FieldError msg={errors.name} /> : <span />}
          <span style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)' }}>{form.name.length}/60</span>
        </div>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Город</FieldLabel>
        <input
          style={{ ...inputStyle, borderColor: errors.city ? '#f44336' : undefined }}
          placeholder="Москва"
          value={form.city}
          onChange={e => setForm({ city: e.target.value })}
        />
        <FieldError msg={errors.city} />
      </div>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Категория</FieldLabel>
        <select
          style={selectStyle}
          value={form.category}
          onChange={e => setForm({ category: e.target.value })}
        >
          {CLUB_CATEGORIES.map(c => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </select>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Тип доступа</FieldLabel>
        <div style={{ display: 'flex', gap: '8px' }}>
          {(['open', 'closed', 'private'] as const).map(type => {
            const labels = { open: 'Открытый', closed: 'Закрытый', private: 'Приватный' }
            const desc = {
              open: 'Любой может вступить',
              closed: 'По заявке организатора',
              private: 'Только по invite-ссылке',
            }
            const active = form.accessType === type
            return (
              <button
                key={type}
                onClick={() => setForm({ accessType: type })}
                style={{
                  flex: 1,
                  padding: '10px 8px',
                  borderRadius: '10px',
                  border: `2px solid ${active ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #e0e0e0)'}`,
                  background: active ? 'rgba(33,150,243,0.1)' : 'transparent',
                  color: active ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-text-color, #000)',
                  cursor: 'pointer',
                  textAlign: 'center',
                  fontSize: '12px',
                  lineHeight: '1.3',
                }}
              >
                <div style={{ fontWeight: 600, marginBottom: '2px' }}>{labels[type]}</div>
                <div style={{ fontSize: '11px', opacity: 0.7 }}>{desc[type]}</div>
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ─── Step 2: Settings + Revenue Calculator ────────────────────────────────────

interface Step2Errors {
  memberLimit?: string
  subscriptionPrice?: string
}

function Step2({
  form,
  setForm,
  errors,
}: {
  form: FormState
  setForm: (f: Partial<FormState>) => void
  errors: Step2Errors
}) {
  const revenue = calcRevenue(form.subscriptionPrice, form.memberLimit)

  return (
    <div>
      <h2 style={{ margin: '0 0 4px', fontSize: '20px' }}>Настройки клуба</h2>
      <p style={{ margin: '0 0 24px', fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)' }}>
        Шаг 2 из 3
      </p>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Лимит участников</FieldLabel>
        <input
          type="number"
          style={{ ...inputStyle, borderColor: errors.memberLimit ? '#f44336' : undefined }}
          value={form.memberLimit}
          min={10}
          max={80}
          onChange={e => setForm({ memberLimit: Number(e.target.value) })}
        />
        <FieldError msg={errors.memberLimit} />
        <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)', marginTop: '4px' }}>От 10 до 80 участников</div>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Цена подписки (Stars/мес)</FieldLabel>
        <input
          type="number"
          style={{ ...inputStyle, borderColor: errors.subscriptionPrice ? '#f44336' : undefined }}
          value={form.subscriptionPrice}
          min={1}
          onChange={e => setForm({ subscriptionPrice: Number(e.target.value) })}
        />
        <FieldError msg={errors.subscriptionPrice} />
      </div>

      {/* Revenue Calculator */}
      <div style={{
        padding: '16px',
        borderRadius: '12px',
        background: 'linear-gradient(135deg, rgba(33,150,243,0.1), rgba(156,39,176,0.08))',
        border: '1px solid rgba(33,150,243,0.2)',
        marginBottom: '16px',
      }}>
        <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span>💰</span> Калькулятор дохода
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div style={{ textAlign: 'center', padding: '12px', borderRadius: '8px', background: 'rgba(76,175,80,0.1)' }}>
            <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '4px' }}>Ваш доход</div>
            <div style={{ fontSize: '22px', fontWeight: 700, color: '#4CAF50' }}>{revenue.organizer.toLocaleString()}</div>
            <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)' }}>Stars/мес (80%)</div>
          </div>
          <div style={{ textAlign: 'center', padding: '12px', borderRadius: '8px', background: 'rgba(33,150,243,0.08)' }}>
            <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '4px' }}>Комиссия платформы</div>
            <div style={{ fontSize: '22px', fontWeight: 700, color: 'var(--tg-theme-button-color, #2196F3)' }}>{revenue.platform.toLocaleString()}</div>
            <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)' }}>Stars/мес (20%)</div>
          </div>
        </div>
        <div style={{ textAlign: 'center', marginTop: '12px', fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>
          При {form.memberLimit} участниках × {form.subscriptionPrice} Stars = {(form.subscriptionPrice * form.memberLimit).toLocaleString()} Stars/мес
        </div>
      </div>
    </div>
  )
}

// ─── Step 3: Content ──────────────────────────────────────────────────────────

interface Step3Errors {
  description?: string
}

function Step3({
  form,
  setForm,
  errors,
}: {
  form: FormState
  setForm: (f: Partial<FormState>) => void
  errors: Step3Errors
}) {
  const fileRef = useRef<HTMLInputElement>(null)

  function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      alert('Размер файла не должен превышать 5 MB')
      return
    }

    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl = reader.result as string
      // Compress via canvas
      const img = new Image()
      img.onload = () => {
        const maxSize = 800
        let { width, height } = img
        if (width > maxSize || height > maxSize) {
          if (width > height) {
            height = Math.round((height * maxSize) / width)
            width = maxSize
          } else {
            width = Math.round((width * maxSize) / height)
            height = maxSize
          }
        }
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')!
        ctx.drawImage(img, 0, 0, width, height)
        const compressed = canvas.toDataURL('image/jpeg', 0.8)
        setForm({ avatarBase64: compressed, avatarPreview: compressed })
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(file)
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 4px', fontSize: '20px' }}>Описание клуба</h2>
      <p style={{ margin: '0 0 24px', fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)' }}>
        Шаг 3 из 3
      </p>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel required>Описание</FieldLabel>
        <textarea
          style={{
            ...inputStyle,
            resize: 'none',
            height: '100px',
            borderColor: errors.description ? '#f44336' : undefined,
          } as React.CSSProperties}
          placeholder="Расскажите о клубе: чем занимаетесь, кого ищете..."
          value={form.description}
          maxLength={500}
          onChange={e => setForm({ description: e.target.value })}
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
          {errors.description ? <FieldError msg={errors.description} /> : <span />}
          <span style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)' }}>{form.description.length}/500</span>
        </div>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel>Правила клуба</FieldLabel>
        <textarea
          style={{ ...inputStyle, resize: 'none', height: '80px' } as React.CSSProperties}
          placeholder="Необязательно. Правила поведения в клубе..."
          value={form.rules}
          onChange={e => setForm({ rules: e.target.value })}
        />
      </div>

      {form.accessType === 'closed' && (
        <div style={{ marginBottom: '16px' }}>
          <FieldLabel>Вопрос для вступления</FieldLabel>
          <input
            style={inputStyle}
            placeholder="Почему хотите вступить в клуб?"
            value={form.applicationQuestion}
            onChange={e => setForm({ applicationQuestion: e.target.value })}
          />
          <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)', marginTop: '4px' }}>
            Будущие участники ответят на этот вопрос при подаче заявки
          </div>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <FieldLabel>Аватар клуба</FieldLabel>
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style={{ display: 'none' }}
          onChange={handleAvatarChange}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div
            onClick={() => fileRef.current?.click()}
            style={{
              width: '72px',
              height: '72px',
              borderRadius: '50%',
              border: '2px dashed var(--tg-theme-hint-color, #ccc)',
              background: form.avatarPreview ? 'none' : 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
              backgroundImage: form.avatarPreview ? `url(${form.avatarPreview})` : 'none',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            {!form.avatarPreview && <span style={{ fontSize: '24px' }}>📷</span>}
          </div>
          <div>
            <button
              onClick={() => fileRef.current?.click()}
              style={{
                padding: '8px 16px',
                borderRadius: '8px',
                border: '1.5px solid var(--tg-theme-button-color, #2196F3)',
                background: 'transparent',
                color: 'var(--tg-theme-button-color, #2196F3)',
                cursor: 'pointer',
                fontSize: '14px',
              }}
            >
              {form.avatarPreview ? 'Изменить' : 'Загрузить фото'}
            </button>
            {form.avatarPreview && (
              <button
                onClick={() => setForm({ avatarBase64: null, avatarPreview: null })}
                style={{
                  display: 'block',
                  marginTop: '6px',
                  padding: '4px 8px',
                  border: 'none',
                  background: 'none',
                  color: '#f44336',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Удалить
              </button>
            )}
            <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)', marginTop: '6px' }}>
              JPG, PNG — не более 5 MB
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Club creation form (multi-step) ─────────────────────────────────────────

interface ClubFormProps {
  onSuccess: (club: Club) => void
  onCancel: () => void
}

function ClubCreationForm({ onSuccess, onCancel }: ClubFormProps) {
  const [step, setStep] = useState(0)
  const [form, setFormState] = useState<FormState>(INITIAL_FORM)
  const [step1Errors, setStep1Errors] = useState<Step1Errors>({})
  const [step2Errors, setStep2Errors] = useState<Step2Errors>({})
  const [step3Errors, setStep3Errors] = useState<Step3Errors>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  function setForm(patch: Partial<FormState>) {
    setFormState(prev => ({ ...prev, ...patch }))
  }

  function validateStep1(): boolean {
    const errs: Step1Errors = {}
    if (!form.name.trim()) errs.name = 'Введите название'
    else if (form.name.length > 60) errs.name = 'Не более 60 символов'
    if (!form.city.trim()) errs.city = 'Введите город'
    setStep1Errors(errs)
    return Object.keys(errs).length === 0
  }

  function validateStep2(): boolean {
    const errs: Step2Errors = {}
    if (form.memberLimit < 10 || form.memberLimit > 80) errs.memberLimit = 'Лимит должен быть от 10 до 80'
    if (form.subscriptionPrice <= 0) errs.subscriptionPrice = 'Цена должна быть больше 0'
    setStep2Errors(errs)
    return Object.keys(errs).length === 0
  }

  function validateStep3(): boolean {
    const errs: Step3Errors = {}
    if (!form.description.trim()) errs.description = 'Введите описание'
    else if (form.description.length > 500) errs.description = 'Не более 500 символов'
    setStep3Errors(errs)
    return Object.keys(errs).length === 0
  }

  function handleNext() {
    if (step === 0 && !validateStep1()) return
    if (step === 1 && !validateStep2()) return
    if (step === 2) {
      handleSubmit()
      return
    }
    setStep(s => s + 1)
  }

  async function handleSubmit() {
    if (!validateStep3()) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const payload: CreateClubRequest = {
        name: form.name.trim(),
        city: form.city.trim(),
        category: form.category,
        accessType: form.accessType,
        memberLimit: form.memberLimit,
        subscriptionPrice: form.subscriptionPrice,
        description: form.description.trim(),
        ...(form.rules.trim() ? { rules: form.rules.trim() } : {}),
        ...(form.accessType === 'closed' && form.applicationQuestion.trim()
          ? { applicationQuestion: form.applicationQuestion.trim() }
          : {}),
        ...(form.avatarBase64 ? { avatarBase64: form.avatarBase64 } : {}),
      }
      const club = await clubsApi.createClub(payload)
      onSuccess(club)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Ошибка создания клуба'
      setSubmitError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const btnStyle: React.CSSProperties = {
    width: '100%',
    padding: '14px',
    borderRadius: '12px',
    border: 'none',
    background: submitting ? 'var(--tg-theme-hint-color, #aaa)' : 'var(--tg-theme-button-color, #2196F3)',
    color: '#fff',
    fontSize: '16px',
    fontWeight: 600,
    cursor: submitting ? 'not-allowed' : 'pointer',
    marginTop: '8px',
  }

  return (
    <div style={{ padding: '16px 16px 32px' }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
        <button
          onClick={step === 0 ? onCancel : () => setStep(s => s - 1)}
          style={{ background: 'none', border: 'none', fontSize: '20px', cursor: 'pointer', padding: '4px 8px 4px 0' }}
        >
          ←
        </button>
        <h1 style={{ margin: 0, fontSize: '18px', flex: 1 }}>Создать клуб</h1>
      </div>

      <StepIndicator current={step} total={3} />

      {step === 0 && <Step1 form={form} setForm={setForm} errors={step1Errors} />}
      {step === 1 && <Step2 form={form} setForm={setForm} errors={step2Errors} />}
      {step === 2 && <Step3 form={form} setForm={setForm} errors={step3Errors} />}

      {submitError && (
        <div style={{ padding: '12px', borderRadius: '8px', background: 'rgba(244,67,54,0.1)', color: '#f44336', marginBottom: '12px', fontSize: '14px' }}>
          {submitError}
        </div>
      )}

      <button style={btnStyle} onClick={handleNext} disabled={submitting}>
        {submitting
          ? 'Создаём клуб...'
          : step < 2
          ? 'Далее →'
          : 'Создать клуб'}
      </button>
    </div>
  )
}

// ─── Success screen ────────────────────────────────────────────────────────────

function SuccessScreen({ club, onGoToClub, onCreateAnother }: { club: Club; onGoToClub: () => void; onCreateAnother: () => void }) {
  return (
    <div style={{ padding: '48px 24px', textAlign: 'center' }}>
      <div style={{ fontSize: '64px', marginBottom: '16px' }}>🎉</div>
      <h2 style={{ margin: '0 0 8px' }}>Клуб создан!</h2>
      <p style={{ margin: '0 0 32px', color: 'var(--tg-theme-hint-color, #888)', fontSize: '14px' }}>
        «{club.name}» успешно создан. Пригласите участников и создайте первое событие.
      </p>
      <button
        onClick={onGoToClub}
        style={{
          width: '100%',
          padding: '14px',
          borderRadius: '12px',
          border: 'none',
          background: 'var(--tg-theme-button-color, #2196F3)',
          color: '#fff',
          fontSize: '16px',
          fontWeight: 600,
          cursor: 'pointer',
          marginBottom: '12px',
        }}
      >
        Открыть клуб
      </button>
      <button
        onClick={onCreateAnother}
        style={{
          width: '100%',
          padding: '14px',
          borderRadius: '12px',
          border: '1.5px solid var(--tg-theme-button-color, #2196F3)',
          background: 'transparent',
          color: 'var(--tg-theme-button-color, #2196F3)',
          fontSize: '16px',
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        Создать ещё один
      </button>
    </div>
  )
}

// ─── Main OrganizerPage ───────────────────────────────────────────────────────

type View = 'dashboard' | 'create' | 'success' | 'manage'

export function OrganizerPage() {
  const navigate = useNavigate()
  const [view, setView] = useState<View>('dashboard')
  const [myClubs, setMyClubs] = useState<Club[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createdClub, setCreatedClub] = useState<Club | null>(null)
  const [managedClub, setManagedClub] = useState<Club | null>(null)

  const fetchMyClubs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const clubs = await clubsApi.getMyClubs()
      setMyClubs(clubs)
    } catch {
      setError('Не удалось загрузить ваши клубы')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (view === 'dashboard') fetchMyClubs()
  }, [view, fetchMyClubs])

  if (view === 'create') {
    return (
      <ClubCreationForm
        onSuccess={club => {
          setCreatedClub(club)
          setView('success')
        }}
        onCancel={() => setView('dashboard')}
      />
    )
  }

  if (view === 'success' && createdClub) {
    return (
      <SuccessScreen
        club={createdClub}
        onGoToClub={() => navigate(`/clubs/${createdClub.id}/interior`)}
        onCreateAnother={() => {
          setCreatedClub(null)
          setView('create')
        }}
      />
    )
  }

  if (view === 'manage' && managedClub) {
    return (
      <OrganizerClubManage
        club={managedClub}
        onBack={() => {
          setManagedClub(null)
          setView('dashboard')
        }}
      />
    )
  }

  // Dashboard view
  return (
    <div style={{ padding: '16px 16px 100px' }}>
      <h1 style={{ margin: '0 0 4px', fontSize: '22px' }}>Мои клубы</h1>
      <p style={{ margin: '0 0 20px', fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)' }}>
        Клубы, где вы организатор
      </p>

      <button
        onClick={() => setView('create')}
        style={{
          width: '100%',
          padding: '14px',
          borderRadius: '12px',
          border: 'none',
          background: 'var(--tg-theme-button-color, #2196F3)',
          color: '#fff',
          fontSize: '16px',
          fontWeight: 600,
          cursor: 'pointer',
          marginBottom: '20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
        }}
      >
        <span>+</span> Создать клуб
      </button>

      {loading ? (
        <>
          <ClubSkeleton />
          <ClubSkeleton />
        </>
      ) : error ? (
        <div style={{ textAlign: 'center', padding: '32px 0' }}>
          <div style={{ fontSize: '13px', color: '#f44336', marginBottom: '12px' }}>{error}</div>
          <button
            onClick={fetchMyClubs}
            style={{
              padding: '10px 20px',
              borderRadius: '8px',
              border: '1.5px solid var(--tg-theme-button-color, #2196F3)',
              background: 'transparent',
              color: 'var(--tg-theme-button-color, #2196F3)',
              cursor: 'pointer',
              fontSize: '14px',
            }}
          >
            Повторить
          </button>
        </div>
      ) : myClubs.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '48px 0', color: 'var(--tg-theme-hint-color, #888)' }}>
          <div style={{ fontSize: '48px', marginBottom: '12px' }}>🏛️</div>
          <div style={{ fontWeight: 600, fontSize: '16px', marginBottom: '8px' }}>У вас пока нет клубов</div>
          <div style={{ fontSize: '14px' }}>Создайте первый клуб и пригласите участников</div>
        </div>
      ) : (
        <div>
          {myClubs.map(club => (
            <div
              key={club.id}
              onClick={() => { setManagedClub(club); setView('manage') }}
              style={{
                padding: '16px',
                borderRadius: '12px',
                background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
                marginBottom: '12px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
              }}
            >
              {club.avatarUrl ? (
                <img
                  src={club.avatarUrl}
                  alt=""
                  style={{ width: '48px', height: '48px', borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }}
                />
              ) : (
                <div style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '50%',
                  background: 'var(--tg-theme-button-color, #2196F3)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  color: '#fff',
                  fontSize: '20px',
                  fontWeight: 700,
                }}>
                  {club.name.charAt(0).toUpperCase()}
                </div>
              )}
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '15px', marginBottom: '4px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {club.name}
                </div>
                <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)' }}>
                  {club.confirmedCount} / {club.memberLimit} участников · {club.subscriptionPrice} Stars
                </div>
              </div>
              <span style={{ fontSize: '18px', color: 'var(--tg-theme-hint-color, #aaa)' }}>›</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
