// File: frontends/admin/src/components/common/RouteErrorBoundary.jsx
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
            <div
                style={{
                    minHeight: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: 24,
                    background: 'var(--ircs-canvas, #0A0D12)',
                    color: 'var(--ircs-text, #F1F5F9)',
                }}
            >
                <div
                    style={{
                        width: '100%',
                        maxWidth: 640,
                        padding: 28,
                        borderRadius: 14,
                        background: 'var(--ircs-paper, #11151E)',
                        border: '1px solid var(--ircs-border, #1E2536)',
                    }}
                >
                    <h2 style={{ margin: '0 0 8px', fontSize: 18, fontWeight: 700 }}>页面渲染失败</h2>
                    <p
                        style={{
                            margin: '0 0 16px',
                            fontSize: 14,
                            lineHeight: 1.6,
                            color: 'var(--ircs-text-secondary, #94A3B8)',
                        }}
                    >
                        当前页面组件遇到运行时错误。你可以点击下方按钮重试，或切换到其他菜单页面继续操作。
                    </p>
                    {this.state.error?.message && (
                        <pre
                            style={{
                                margin: '0 0 18px',
                                overflow: 'auto',
                                padding: 14,
                                borderRadius: 10,
                                fontSize: 12.5,
                                lineHeight: 1.55,
                                background: 'var(--ircs-subtle, #171C28)',
                                border: '1px solid var(--ircs-border, #1E2536)',
                                color: 'var(--ircs-error, #F87171)',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-word',
                            }}
                        >
                            {this.state.error.message}
                        </pre>
                    )}
                    <button
                        type="button"
                        onClick={() => this.setState({ hasError: false, error: null })}
                        style={{
                            padding: '8px 16px',
                            border: 0,
                            borderRadius: 8,
                            fontSize: 13.5,
                            fontWeight: 600,
                            color: 'var(--ircs-primary-contrast, #FFFFFF)',
                            background: 'var(--ircs-primary, #4F46E5)',
                            cursor: 'pointer',
                        }}
                    >
                        重试加载
                    </button>
                </div>
            </div>
        );
    }
}

export default RouteErrorBoundary;
