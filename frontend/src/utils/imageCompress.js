// 업로드 전에 브라우저에서 이미지를 줄이고 압축한다.
// 서버(Spring)와 프록시(nginx) 업로드 한도는 10MB다.
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export const PROFILE_IMAGE_OPTIONS = { maxSide: 512 }
export const CHAT_IMAGE_OPTIONS = { maxSide: 1920 }

// 긴 변이 maxSide를 넘지 않도록 비율을 유지해 줄인 크기
export function fitWithin(width, height, maxSide) {
  const longest = Math.max(width, height)
  if (longest <= maxSide) return { width, height }
  const ratio = maxSide / longest
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
  }
}

// 서버는 파일 이름의 확장자로도 이미지를 검사하므로, 바뀐 형식에 맞게 확장자를 바꾼다
export function renameForType(name, mimeType) {
  const ext = mimeType === "image/webp" ? "webp" : mimeType === "image/png" ? "png" : "jpg"
  const base = (name || "").replace(/\.[^./\\]+$/, "") || "image"
  return `${base}.${ext}`
}

export function formatBytes(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
  return `${Math.max(1, Math.round(bytes / 1024))}KB`
}

// 업로드 실패 응답을 사용자에게 보여줄 문장으로 바꾼다
export function uploadErrorMessage(error, fallback) {
  if (error?.response?.status === 413) {
    return `이미지 용량이 너무 큽니다. ${formatBytes(MAX_UPLOAD_BYTES)} 이하로 올려주세요.`
  }
  return error?.response?.data?.message || fallback
}

function loadImage(file) {
  if (typeof createImageBitmap === "function") {
    return createImageBitmap(file)
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => { URL.revokeObjectURL(url); resolve(img) }
    img.onerror = (e) => { URL.revokeObjectURL(url); reject(e) }
    img.src = url
  })
}

function canvasToBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

/**
 * 이미지를 긴 변 maxSide 이하로 줄이고 WebP(미지원 브라우저는 JPEG)로 압축한다.
 * GIF(움짤 유지), 브라우저가 못 읽는 형식, 압축해도 커지는 경우는 원본을 그대로 돌려준다.
 */
export async function compressImage(file, { maxSide = 1920, quality = 0.82, skipBelowBytes = 300 * 1024 } = {}) {
  if (!file || !file.type?.startsWith("image/") || file.type === "image/gif") return file

  let source
  try {
    source = await loadImage(file)
  } catch {
    return file
  }

  const { width, height } = fitWithin(source.width, source.height, maxSide)
  const resized = width !== source.width || height !== source.height
  if (!resized && file.size <= skipBelowBytes) {
    source.close?.()
    return file
  }

  const canvas = document.createElement("canvas")
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext("2d")
  ctx.drawImage(source, 0, 0, width, height)
  source.close?.()

  let blob = await canvasToBlob(canvas, "image/webp", quality)
  if (!blob || blob.type !== "image/webp") {
    // WebP 인코딩을 못 하는 브라우저: 투명 부분이 검게 되지 않도록 흰 배경을 깔고 JPEG로
    ctx.globalCompositeOperation = "destination-over"
    ctx.fillStyle = "#ffffff"
    ctx.fillRect(0, 0, width, height)
    blob = await canvasToBlob(canvas, "image/jpeg", quality)
  }
  if (!blob || (!resized && blob.size >= file.size)) return file

  return new File([blob], renameForType(file.name, blob.type), { type: blob.type, lastModified: Date.now() })
}
