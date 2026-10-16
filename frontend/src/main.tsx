import React from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './upstream/theme/base.css'
import './upstream/theme/design-platform.css'
import './upstream/theme/focus.css'
import './upstream/theme/scrollbar.css'
import './upstream/theme/corner-shape.css'
import './style.css'

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>)
