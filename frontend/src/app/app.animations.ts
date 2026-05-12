import { animate, query, style, transition, trigger } from '@angular/animations';

/** Entrada tipo HUD — mesmo easing do dashboard (hudFadeUp). */
export const hudRouteAnimations = trigger('hudRouteAnimations', [
  transition('* => *', [
    query(
      ':enter',
      [
        style({ opacity: 0, transform: 'translateY(18px)' }),
        animate(
          '0.55s cubic-bezier(0.22, 1, 0.36, 1)',
          style({ opacity: 1, transform: 'translateY(0)' })
        ),
      ],
      { optional: true }
    ),
  ]),
]);
