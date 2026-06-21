import React from 'react';
import { AsyncBoundary } from './AsyncBoundary';
import { OverlayProvider } from './OverlayProvider';

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <AsyncBoundary>
      <OverlayProvider>
        {children}
      </OverlayProvider>
    </AsyncBoundary>
  );
}
