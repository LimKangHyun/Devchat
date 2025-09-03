"use client"

import { useState, useCallback } from "react"
import { ChatModal } from "./chat-modal"

const BASE_Z_INDEX = 10000 // Base z-index for chat modals

export function ChatManager({ currentUser, children }) {
  const [openChats, setOpenChats] = useState([])

  const openChatModal = useCallback((friend) => {
    if (!friend || !friend.username) {
      console.error("ChatManager: openChatModal called with invalid friend object", friend)
      return
    }

    setOpenChats((prevOpenChats) => {
      const existingChatIndex = prevOpenChats.findIndex((chat) => chat.friend.username === friend.username)
      const updatedChats = [...prevOpenChats]

      if (existingChatIndex !== -1) {
        // Move existing chat to the end to bring it to front (highest z-index)
        const chatToMove = updatedChats.splice(existingChatIndex, 1)[0]
        updatedChats.push(chatToMove)
      } else {
        // Add new chat
        const newChat = {
          id: Date.now(), // Unique ID for the chat session
          friend,
          // Initial position can be staggered or fixed
          position: { x: 100 + prevOpenChats.length * 20, y: 100 + prevOpenChats.length * 20 },
        }
        updatedChats.push(newChat)
      }
      return updatedChats
    })
  }, [])

  const closeChatModal = useCallback((chatId) => {
    setOpenChats((prevOpenChats) => prevOpenChats.filter((chat) => chat.id !== chatId))
  }, [])

  return (
    <>
      {/* Pass openChatModal and currentUser to children */}
      {typeof children === "function" ? children({ openChatModal, currentUser }) : children}

      {/* Render all open chat modals */}
      {openChats.map((chat, index) => {
        const zIndex = BASE_Z_INDEX + index
        return (
          <ChatModal
            key={chat.id}
            isOpen={true}
            onClose={() => closeChatModal(chat.id)}
            friend={chat.friend}
            currentUser={currentUser}
            initialPosition={chat.position}
            style={{ zIndex }}
          />
        )
      })}
    </>
  )
}
