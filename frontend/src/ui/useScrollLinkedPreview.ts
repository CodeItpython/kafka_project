import { type RefObject } from 'react';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

type PreviewDependency = string | number | boolean | null | undefined;

gsap.registerPlugin(useGSAP, ScrollTrigger);

export function useScrollLinkedPreview(
  containerRef: RefObject<HTMLElement | null>,
  dependencies: PreviewDependency[],
  onActiveChange: (id: string) => void
) {
  useGSAP(() => {
    const container = containerRef.current;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (!container || reduceMotion) {
      return undefined;
    }

    const items = Array.from(container.querySelectorAll<HTMLElement>('[data-preview-id]'));
    if (items.length === 0) {
      return undefined;
    }

    const triggers = items.map((item) => {
      const id = item.dataset.previewId;

      return ScrollTrigger.create({
        trigger: item,
        scroller: container,
        start: 'top 58%',
        end: 'bottom 42%',
        onEnter: () => {
          if (id) onActiveChange(id);
        },
        onEnterBack: () => {
          if (id) onActiveChange(id);
        }
      });
    });

    ScrollTrigger.refresh();

    return () => {
      triggers.forEach((trigger) => trigger.kill());
    };
  }, {
    scope: containerRef,
    dependencies,
    revertOnUpdate: true
  });
}
