import React from 'react';

class RouteErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        console.error('Route render failed', error, errorInfo);
    }

    componentDidUpdate(prevProps) {
        if (prevProps.resetKey !== this.props.resetKey && this.state.hasError) {
            this.setState({ hasError: false, error: null });
        }
    }

    render() {
        if (!this.state.hasError) {
            return this.props.children;
        }

        return (
            <div style={{ padding: 24, color: 'rgba(255,255,255,0.82)' }}>
                <h2 style={{ margin: '0 0 12px', fontSize: 20 }}>页面渲染失败</h2>
                <p style={{ margin: '0 0 16px', maxWidth: 720, color: 'rgba(255,255,255,0.64)' }}>
                    当前页面组件遇到运行时错误。你可以刷新或切换到其他页面继续操作。
                </p>
                {this.state.error?.message && (
                    <pre style={{
                        maxWidth: 960,
                        overflow: 'auto',
                        padding: 12,
                        borderRadius: 4,
                        background: 'rgba(255,255,255,0.06)',
                        color: '#ffb4ab'
                    }}>
                        {this.state.error.message}
                    </pre>
                )}
                <button
                    type="button"
                    onClick={() => this.setState({ hasError: false, error: null })}
                    style={{
                        marginTop: 12,
                        padding: '8px 14px',
                        border: 0,
                        borderRadius: 4,
                        color: '#071018',
                        background: '#90caf9',
                        cursor: 'pointer'
                    }}
                >
                    重试
                </button>
            </div>
        );
    }
}

export default RouteErrorBoundary;
