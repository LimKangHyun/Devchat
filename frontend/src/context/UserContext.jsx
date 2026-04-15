import { createContext, useContext, useEffect, useState } from "react"
import axiosInstance from "../components/api/axiosInstance"

const UserContext = createContext(null)

export const UserProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null)
  const [isLoading, setIsLoading] = useState(true)

  const fetchUser = async () => {
    try {
      setIsLoading(true)
      const { data } = await axiosInstance.get("/user/details")
      setCurrentUser(data)
    } catch (err) {
      // 로그인 안 된 상태면 null로 두고 끝냄
      setCurrentUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchUser()
    window.addEventListener("profile-updated", fetchUser)
    return () => window.removeEventListener("profile-updated", fetchUser)
  }, [])

  return (
    <UserContext.Provider value={{ currentUser, isLoading, fetchUser }}>
      {children}
    </UserContext.Provider>
  )
}

export const useUser = () => useContext(UserContext)