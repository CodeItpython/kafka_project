import React, { Suspense } from 'react';

type AsyncBoundaryProps = {
  children: React.ReactNode;
  pendingFallback?: React.ReactNode;
  rejectedFallback?: (error: Error, reset: () => void) => React.ReactNode;
};

type AsyncBoundaryState = {
  error: Error | null;
};

export class AsyncBoundary extends React.Component<AsyncBoundaryProps, AsyncBoundaryState> {
  state: AsyncBoundaryState = {
    error: null
  };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  reset = () => {
    this.setState({ error: null });
  };

  render() {
    const {
      children,
      pendingFallback = <AppFallback message="화면을 불러오는 중입니다." />,
      rejectedFallback = (error, reset) => <AppErrorFallback error={error} onReset={reset} />
    } = this.props;

    if (this.state.error) {
      return rejectedFallback(this.state.error, this.reset);
    }

    return (
      <Suspense fallback={pendingFallback}>
        {children}
      </Suspense>
    );
  }
}

function AppFallback({ message }: { message: string }) {
  return (
    <main className="app-fallback" aria-busy="true">
      <div className="app-fallback-panel">
        <span className="app-loading-dot" aria-hidden />
        <p>{message}</p>
      </div>
    </main>
  );
}

function AppErrorFallback({
  error,
  onReset
}: {
  error: Error;
  onReset: () => void;
}) {
  return (
    <main className="app-fallback">
      <section className="app-fallback-panel app-error-panel">
        <strong>화면을 다시 불러오지 못했습니다.</strong>
        <p>{error.message || '잠시 후 다시 시도해주세요.'}</p>
        <button type="button" onClick={onReset}>다시 시도</button>
      </section>
    </main>
  );
}
