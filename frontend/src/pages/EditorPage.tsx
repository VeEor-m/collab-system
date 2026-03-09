import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import Editor from '../components/Editor'
import { documentApi } from '../services/api'

interface Document {
  id: string
  title: string
  ownerId: string
}

export default function EditorPage() {
  const { docId } = useParams<{ docId: string }>()
  const [document, setDocument] = useState<Document | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!docId) return

    documentApi
      .get(docId)
      .then((doc) => {
        setDocument(doc)
      })
      .catch(console.error)
      .finally(() => {
        setLoading(false)
      })
  }, [docId])

  if (loading) {
    return <div>Loading...</div>
  }

  if (!document) {
    return (
      <div>
        <p>Document not found</p>
        <Link to="/">Back to documents</Link>
      </div>
    )
  }

  return (
    <div>
      <div style={{ padding: '1rem 2rem', borderBottom: '1px solid #e5e5e5' }}>
        <Link to="/" style={{ color: '#666', textDecoration: 'none' }}>
          ← Back to Documents
        </Link>
        <h2 style={{ marginTop: '0.5rem' }}>{document.title}</h2>
      </div>
      <Editor docId={docId!} />
    </div>
  )
}
