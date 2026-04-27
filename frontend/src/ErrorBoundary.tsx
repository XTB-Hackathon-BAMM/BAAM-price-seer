import { Component, type ReactNode } from 'react'

interface State {
  error?: Error
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = {}

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: unknown) {
    console.error('App crashed:', error, info)
  }

  render() {
    if (this.state.error) {
      return (
        <div
          style={{
            padding: 24,
            margin: 24,
            background: '#1a1a24',
            border: '2px solid #ef4444',
            borderRadius: 10,
            color: '#e6e6ee',
            fontFamily: 'ui-monospace, Menlo, monospace',
          }}
        >
          <h2 style={{ color: '#ef4444', marginTop: 0 }}>App crashed</h2>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
            {this.state.error.message}
            {'\n\n'}
            {this.state.error.stack}
          </pre>
        </div>
      )
    }
    return this.props.children
  }
}
