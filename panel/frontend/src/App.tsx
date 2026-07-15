import { Navigate, Route, Routes } from 'react-router-dom'

// ponytail: stub pages; auth shell and real content arrive in frontend steps 2-5
function Page({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
    </main>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Page title="Вход" />} />
      <Route path="/board" element={<Page title="Доска" />} />
      <Route path="/timeline" element={<Page title="Лента" />} />
      <Route path="/reports/:id" element={<Page title="Заявка" />} />
      <Route path="/users" element={<Page title="Пользователи" />} />
      <Route path="/servers" element={<Page title="Серверы" />} />
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  )
}
