"use client"

import { MessageCircle } from "lucide-react"
import styles from "./chat-button.module.css"

export function ChatButton({ onClick }) {
  return (
    <button className={styles.chatButton} onClick={onClick} aria-label="Start Chat">
      <MessageCircle size={20} />
    </button>
  )
}
