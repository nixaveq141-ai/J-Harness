// Pill: capsule at the 24px text-line size, selectable when given `onClick`
// (view switcher tabs, filters) and a static span otherwise — TerminalBlock's
// exit status is the read-only case. The 11px read-only badge is `Tag`; size
// separates the two as much as interactivity does.

import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react'
import clsx from 'clsx'
import css from './Pill.module.css'

/**
 * Render a pill chip. Interactive when onClick is supplied (renders a button);
 * otherwise a static span.
 * @param props.active - selected/active visual state.
 * @returns pill element.
 */
type PillProps = {
  active?: boolean
  // `| undefined` so a caller can forward an optional class straight through
  // under exactOptionalPropertyTypes (a CSS-module lookup is string|undefined).
  className?: string | undefined
  children?: ReactNode
} & ((Omit<HTMLAttributes<HTMLSpanElement>, 'onClick'> & { onClick?: never }) | (ButtonHTMLAttributes<HTMLButtonElement> & { onClick: NonNullable<ButtonHTMLAttributes<HTMLButtonElement>['onClick']> }))
export function Pill(props: PillProps) {
  if (props.onClick) {
    const { active = false, className, children, onClick, type = 'button', ...rest } = props
    return <button type={type} className={clsx(css.pill, css.interactive, active && css.active, className)} onClick={onClick} {...rest}>{children}</button>
  }
  const { active = false, className, children, onClick: _onClick, ...rest } = props
  return <span className={clsx(css.pill, active && css.active, className)} {...rest}>{children}</span>
}
