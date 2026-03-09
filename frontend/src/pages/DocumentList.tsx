import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { documentApi } from '../services/api'
import { useAuthStore } from '../store/authStore'

interface Document {
  id: string
  title: string
  ownerId: string
  createdAt: string
  updatedAt: string
}

export default function DocumentList() {
  const [documents, setDocuments] = useState<Document[]>([])
  const [loading, setLoading] = useState(true)
  const [newTitle, setNewTitle] = useState('')
  const navigate = useNavigate()
  const logout = useAuthStore((state) => state.logout)

  useEffect(() => {
    loadDocuments()
  }, [])

  const loadDocuments = async () => {
    try {
      const docs = await documentApi.list()
      setDocuments(docs)
    } catch (error) {
      console.error('Failed to load documents:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTitle.trim()) return

    try {
      const doc = await documentApi.create(newTitle)
      setDocuments([...documents, doc])
      setNewTitle('')
      navigate(`/doc/${doc.id}`)
    } catch (error) {
      console.error('Failed to create document:', error)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await documentApi.delete(id)
      setDocuments(documents.filter((d) => d.id !== id))
    } catch (error) {
      console.error('Failed to delete document:', error)
    }
  }

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>My Documents</h1>
        <button onClick={logout}>Logout</button>
      </div>

      <form onSubmit={handleCreate} style={{ display: 'flex', gap: '1rem', marginBottom: '2rem' }}>
        <input
          type="text"
          placeholder="New document title"
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          style={{ flex: 1 }}
        />
        <button type="submit">Create</button>
      </form>

      {loading ? (
        <p>Loading...</p>
      ) : documents.length === 0 ? (
        <p>No documents yet. Create one to get started!</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {documents.map((doc) => (
            <li
              key={doc.id}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '1rem',
                border: '1px solid #e5e5e5',
                borderRadius: '8px',
                marginBottom: '0.5rem',
              }}
            >
              <Link to={`/doc/${doc.id}`} style={{ fontWeight: 'bold', color: '#333' }}>
                {doc.title}
              </Link>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.875rem', color: '#666' }}>
                  Updated: {new Date(doc.updatedAt).toLocaleDateString()}
                </span>
                <button onClick={() => handleDelete(doc.id)} style={{ color: '#ef4444' }}>
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
