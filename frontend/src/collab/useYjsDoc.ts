import { useEffect, useState, useRef } from 'react'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { IndexeddbPersistence } from 'y-indexeddb'
import { useAuthStore } from '../store/authStore'

export const useYjsDoc = (docId: string) => {
  const { token, userId } = useAuthStore()
  const [ydoc] = useState(() => new Y.Doc())
  const [status, setStatus] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const providerRef = useRef<WebsocketProvider | null>(null)
  const indexeddbRef = useRef<IndexeddbPersistence | null>(null)

  useEffect(() => {
    if (!docId || !token) return

    // 1. Persistent to IndexedDB (offline support)
    const indexeddb = new IndexeddbPersistence(docId, ydoc)
    indexeddbRef.current = indexeddb

    indexeddb.whenSynced.then(() => {
      console.log('IndexedDB content loaded')
    })

    // 2. WebSocket connection (with JWT)
    const wsUrl = `${import.meta.env.VITE_WS_URL || 'ws://localhost:8080'}/collab`

    const provider = new WebsocketProvider(
      wsUrl,
      docId,
      ydoc,
      {
        params: { token },
        reconnect: true,
        reconnectInterval: 2000,
      }
    )
    providerRef.current = provider

    provider.on('status', (event: { status: string }) => {
      setStatus(event.status as 'connecting' | 'connected' | 'disconnected')
    })

    return () => {
      provider.destroy()
      indexeddb.destroy()
    }
  }, [docId, token, ydoc])

  return { ydoc, status }
}
