import { Link } from 'react-router-dom'
import './App.css'

function App() {
  return (
    <main className="app-shell">
      <section className="foundation-card" aria-labelledby="app-title">
        <p className="eyebrow">Application foundation</p>
        <h1 id="app-title">Clipping Growth OS</h1>
        <p className="foundation-status">Frontend foundation ready</p>
      </section>
    </main>
  )
}

export function NotFound() {
  return (
    <main className="app-shell">
      <section className="foundation-card" aria-labelledby="not-found-title">
        <p className="eyebrow">404</p>
        <h1 id="not-found-title">Page not found</h1>
        <Link className="home-link" to="/">
          Return home
        </Link>
      </section>
    </main>
  )
}

export default App
