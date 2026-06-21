import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { X } from 'lucide-react';

type OverlayToast = {
  id: number;
  message: string;
};

type OverlayContextValue = {
  toast: (message: string) => void;
  close: (id: number) => void;
};

const OverlayContext = createContext<OverlayContextValue | null>(null);

export function OverlayProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<OverlayToast[]>([]);

  const close = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const toast = useCallback((message: string) => {
    const id = Date.now() + Math.floor(Math.random() * 1000);
    setToasts((current) => [...current, { id, message }].slice(-3));
    window.setTimeout(() => close(id), 2800);
  }, [close]);

  const value = useMemo(() => ({ toast, close }), [toast, close]);

  return (
    <OverlayContext.Provider value={value}>
      {children}
      <div className="overlay-root" aria-live="polite" aria-atomic="true">
        <AnimatePresence initial={false}>
          {toasts.map((item) => (
            <motion.div
              key={item.id}
              className="overlay-toast"
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.16, ease: 'easeOut' }}
            >
              <span>{item.message}</span>
              <button type="button" onClick={() => close(item.id)} title="알림 닫기">
                <X size={15} aria-hidden />
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </OverlayContext.Provider>
  );
}

export function useOverlay() {
  const context = useContext(OverlayContext);
  if (!context) {
    throw new Error('useOverlay must be used within OverlayProvider.');
  }
  return context;
}
