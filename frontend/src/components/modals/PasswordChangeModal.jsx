"use client"

import { useState } from "react"
import { Loader2, X } from "lucide-react"
import styles from "../PasswordChangeModal.module.css"
import useBackClose from "../../hooks/useBackClose"

export default function PasswordChangeModal({ isOpen, onClose, axiosInstance }) {
  useBackClose(isOpen, onClose)
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
      newErrors.currentPassword = "현재 비밀번호를 입력해주세요."
    }

    if (!formData.newPassword) {
      newErrors.newPassword = "새 비밀번호를 입력해주세요."
    } else if (formData.newPassword.length < 4) {
      newErrors.newPassword = "비밀번호는 4자 이상이어야 합니다."
    } else if (formData.newPassword === formData.currentPassword) {
      newErrors.newPassword = "새 비밀번호는 현재 비밀번호와 달라야 합니다."
    }

    if (!formData.confirmPassword) {
      newErrors.confirmPassword = "새 비밀번호를 한 번 더 입력해주세요."
    } else if (formData.newPassword !== formData.confirmPassword) {
      newErrors.confirmPassword = "비밀번호가 일치하지 않습니다."
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
    <div className="dc-modal-backdrop">
      <div className="dc-modal" style={{ maxWidth: 440 }}>
        <div className="dc-modal-header">
          <h2 className="dc-modal-title">비밀번호 변경</h2>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className="dc-modal-body">
            <p className={styles.description}>
              현재 비밀번호와 새로 사용할 비밀번호를 입력하세요.
            </p>

            <div className={styles.formGroup}>
              <label className="dc-label" htmlFor="pw-current">현재 비밀번호</label>
              <input
                id="pw-current"
                type="password"
                placeholder="현재 비밀번호"
                value={formData.currentPassword}
                onChange={(e) => handleInputChange("currentPassword", e.target.value)}
                className={`dc-input ${errors.currentPassword ? styles.inputError : ""}`}
              />
              {errors.currentPassword && <p className={styles.errorText}>{errors.currentPassword}</p>}
            </div>

            <div className={styles.formGroup}>
              <label className="dc-label" htmlFor="pw-new">새 비밀번호</label>
              <input
                id="pw-new"
                type="password"
                placeholder="새 비밀번호"
                value={formData.newPassword}
                onChange={(e) => handleInputChange("newPassword", e.target.value)}
                className={`dc-input ${errors.newPassword ? styles.inputError : ""}`}
                minLength="4"
              />
              {errors.newPassword && <p className={styles.errorText}>{errors.newPassword}</p>}
            </div>

            <div className={styles.formGroup}>
              <label className="dc-label" htmlFor="pw-confirm">새 비밀번호 확인</label>
              <input
                id="pw-confirm"
                type="password"
                placeholder="비밀번호 확인"
                value={formData.confirmPassword}
                onChange={(e) => handleInputChange("confirmPassword", e.target.value)}
                className={`dc-input ${errors.confirmPassword ? styles.inputError : ""}`}
                minLength="4"
              />
              {errors.confirmPassword && <p className={styles.errorText}>{errors.confirmPassword}</p>}
            </div>
          </div>

          <div className="dc-modal-footer">
            <button type="button" className="dc-btn" onClick={onClose} disabled={isSubmitting}>
              취소
            </button>
            <button type="submit" className="dc-btn dc-btn-primary" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 size={15} className={styles.spinner} />
                  변경 중...
                </>
              ) : (
                "비밀번호 변경"
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
