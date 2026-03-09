import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCursor from '@tiptap/extension-collaboration-cursor'
import { useYjsDoc } from '../collab/useYjsDoc'
import { useAuthStore } from '../store/authStore'
import * as Y from 'yjs'
import './Editor.css'

interface EditorProps {
  docId: string
}

const colors = ['#958DF1', '#F98181', '#FBBC88', '#FAF594', '#70CFF8', '#94FADB', '#B9F18D']

export default function Editor({ docId }: EditorProps) {
  const { ydoc, status } = useYjsDoc(docId)
  const { userId } = useAuthStore()

  const yXmlFragment = ydoc.getXmlFragment('tiptap')

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        history: false,
      }),
      Collaboration.configure({
        document: ydoc,
        fragment: yXmlFragment,
      }),
      CollaborationCursor.configure({
        provider: null,
        user: {
          name: userId?.slice(0, 8) || 'Anonymous',
          color: colors[Math.floor(Math.random() * colors.length)],
        },
      }),
    ],
    editorProps: {
      attributes: {
        class: 'editor-content',
      },
    },
  })

  if (!editor) {
    return <div>Loading editor...</div>
  }

  return (
    <div className="editor-container">
      <div className="editor-status">
        <span className={`status-indicator ${status}`}></span>
        {status === 'connected' ? 'Connected' : status === 'connecting' ? 'Connecting...' : 'Disconnected'}
      </div>
      <div className="toolbar">
        <button
          onClick={() => editor.chain().focus().toggleBold().run()}
          className={editor.isActive('bold') ? 'is-active' : ''}
        >
          Bold
        </button>
        <button
          onClick={() => editor.chain().focus().toggleItalic().run()}
          className={editor.isActive('italic') ? 'is-active' : ''}
        >
          Italic
        </button>
        <button
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
          className={editor.isActive('heading', { level: 1 }) ? 'is-active' : ''}
        >
          H1
        </button>
        <button
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          className={editor.isActive('heading', { level: 2 }) ? 'is-active' : ''}
        >
          H2
        </button>
        <button
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          className={editor.isActive('bulletList') ? 'is-active' : ''}
        >
          Bullet List
        </button>
        <button
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          className={editor.isActive('orderedList') ? 'is-active' : ''}
        >
          Ordered List
        </button>
      </div>
      <EditorContent editor={editor} />
    </div>
  )
}
