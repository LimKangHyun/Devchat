"use client"

import { useState } from "react"
import { Loader2, X } from "lucide-react"
import styles from "../PasswordChangeModal.module.css"

export default function PasswordChangeModal({ isOpen, onClose, axiosInstance }) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formData, setFormData] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  })
  const [errors, setErrors] = useState({})

  const validateForm = () => {
    const newErrors = {}

    if (!formData.currentPassword) {
      newErrors.currentPassword = "Current password is required"
    }

    if (!formData.newPassword) {
      newErrors.newPassword = "New password is required"
    } else if (formData.newPassword.length < 4) {
      newErrors.newPassword = "Password must be at least 4 characters"
    } else if (formData.newPassword === formData.currentPassword) {
      newErrors.newPassword = "The new password must be different from the current password."
    }

    if (!formData.confirmPassword) {
      newErrors.confirmPassword = "Please confirm your password"
    } else if (formData.newPassword !== formData.confirmPassword) {
      newErrors.confirmPassword = "Passwords do not match"
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleInputChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }))
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: "" }))
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    if (!validateForm()) {
      return
    }

    setIsSubmitting(true)

    try {
      const updateData = {
        currentPassword: formData.currentPassword,
        newPassword: formData.newPassword,
        confirmPassword: formData.confirmPassword
      };

      await axiosInstance.put("/user/password", updateData, {
        headers: { 'Content-Type': 'application/json' }
      });

      alert("비밀번호가 성공적으로 변경되었습니다!")

      setFormData({
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
      })
      setErrors({})
      onClose()
    } catch (error) {
      const msg = error?.response?.data?.message || "비밀번호 변경에 실패했습니다."
      alert(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.header}>
          <h2 className={styles.title}>Change Password</h2>
          <p className={styles.description}>
            Enter your current password and a new password to update your credentials.
          </p>
          <button className={styles.closeButton} onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formGroup}>
            <label className={styles.label}>Current Password</label>
            <input
              type="password"
              placeholder="현재 비밀번호"
              value={formData.currentPassword}
              onChange={(e) => handleInputChange("currentPassword", e.target.value)}
              className={`${styles.input} ${errors.currentPassword ? styles.inputError : ""}`}
            />
            {errors.currentPassword && <p className={styles.errorText}>{errors.currentPassword}</p>}
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>New Password</label>
            <input
              type="password"
              placeholder="새 비밀번호"
              value={formData.newPassword}
              onChange={(e) => handleInputChange("newPassword", e.target.value)}
              className={`${styles.input} ${errors.newPassword ? styles.inputError : ""}`}
              minLength="4"
            />
            {errors.newPassword && <p className={styles.errorText}>{errors.newPassword}</p>}
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>Confirm New Password</label>
            <input
              type="password"
              placeholder="비밀번호 확인"
              value={formData.confirmPassword}
              onChange={(e) => handleInputChange("confirmPassword", e.target.value)}
              className={`${styles.input} ${errors.confirmPassword ? styles.inputError : ""}`}
              minLength="4"
            />
            {errors.confirmPassword && <p className={styles.errorText}>{errors.confirmPassword}</p>}
          </div>

          <div className={styles.footer}>
            <button type="button" className={styles.cancelButton} onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className={styles.spinner} />
                  Updating...
                </>
              ) : (
                "Update Password"
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
