import { useRef, type RefObject } from 'react';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(useGSAP, ScrollTrigger);

type ScrollRevealDependency = string | number | boolean | null | undefined;

const REVEAL_SELECTOR = [
  '.profile-card',
  '.profile-editor',
  '.search-row',
  '.panel-section',
  '.directory-header',
  '.directory-section',
  '.summary-card',
  '.chat-bubble',
  '.empty-state',
  '.notice'
].join(', ');

const SCROLL_CONTAINER_SELECTOR = [
  '.sidebar-scroll',
  '.room-directory',
  '.message-list'
].join(', ');

export function useScrollReveal(
  scopeRef: RefObject<HTMLElement | null>,
  dependencies: ScrollRevealDependency[]
) {
  const reduceMotionRef = useRef(false);

  useGSAP(() => {
    reduceMotionRef.current = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const scope = scopeRef.current;

    if (!scope || reduceMotionRef.current) {
      return undefined;
    }

    const triggers: ScrollTrigger[] = [];
    const containers = Array.from(scope.querySelectorAll<HTMLElement>(SCROLL_CONTAINER_SELECTOR));

    containers.forEach((container) => {
      const targets = Array.from(container.querySelectorAll<HTMLElement>(REVEAL_SELECTOR))
        .filter((target) => !target.closest('.sidebar-footer'));

      gsap.set(targets, {
        autoAlpha: 0,
        y: 18,
        willChange: 'opacity, transform'
      });

      targets.forEach((target, index) => {
        const tween = gsap.to(target, {
          autoAlpha: 1,
          y: 0,
          duration: 0.48,
          delay: Math.min(index * 0.018, 0.12),
          ease: 'power3.out',
          clearProps: 'willChange',
          scrollTrigger: {
            trigger: target,
            scroller: container,
            start: 'top 94%',
            once: true
          }
        });

        if (tween.scrollTrigger) {
          triggers.push(tween.scrollTrigger);
        }
      });
    });

    return () => {
      triggers.forEach((trigger) => trigger.kill());
    };
  }, {
    scope: scopeRef,
    dependencies,
    revertOnUpdate: true
  });
}
