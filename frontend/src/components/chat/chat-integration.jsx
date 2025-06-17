"use client"

import { useState } from "react"
import { FriendsListModal } from "./friends-list-modal"
import { ChatModal } from "./chat-modal"

export function ChatIntegration({ currentUser, children }) {
  const [isFriendsListOpen, setIsFriendsListOpen] = useState(false)
  const [openChats, setOpenChats] = useState([])

  const openChatModal = (friend) => {
    const existingChat = openChats.find((chat) => chat.friend.username === friend.username)
    if (!existingChat) {
      setOpenChats((prev) => [...prev, { id: Date.now(), friend }])
    }
  }

  const closeChatModal = (chatId) => {
    setOpenChats((prev) => prev.filter((chat) => chat.id !== chatId))
  }

  const handleStartChat = (friend) => {
    openChatModal(friend)
  }

  const openFriendsList = () => {
    setIsFriendsListOpen(true)
  }

  return (
    <>
      {children({ openFriendsList, handleStartChat })}

      {/* Friends List Modal */}
      <FriendsListModal
        isOpen={isFriendsListOpen}
        onClose={() => setIsFriendsListOpen(false)}
        onStartChat={handleStartChat}
        currentUser={currentUser}
      />

      {/* Chat Modals */}
      {openChats.map((chat) => (
        <ChatModal
          key={chat.id}
          isOpen={true}
          onClose={() => closeChatModal(chat.id)}
          friend={chat.friend}
          currentUser={currentUser}
        />
      ))}
    </>
  )
}
