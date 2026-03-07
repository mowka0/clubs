import { Component, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    console.error('ErrorBoundary caught:', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            padding: '24px',
            textAlign: 'center',
            gap: '16px',
          }}
        >
          <div style={{ fontSize: '48px' }}>&#9888;</div>
          <h2 style={{ margin: 0 }}>Что-то пошло не так</h2>
          <p style={{ margin: 0, opacity: 0.7, fontSize: '14px' }}>
            {this.state.error?.message ?? 'Неизвестная ошибка'}
          </p>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
            style={{
              padding: '10px 24px',
              borderRadius: '8px',
              border: 'none',
              cursor: 'pointer',
              fontSize: '16px',
            }}
          >
            Попробовать снова
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
