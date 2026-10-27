import { fitWithin, renameForType, uploadErrorMessage, compressImage } from "./imageCompress"

describe("fitWithin", () => {
  it("작은 이미지는 그대로 둔다", () => {
    expect(fitWithin(800, 600, 1920)).toEqual({ width: 800, height: 600 })
  })

  it("긴 변을 maxSide에 맞추고 비율을 유지한다", () => {
    expect(fitWithin(4032, 3024, 1920)).toEqual({ width: 1920, height: 1440 })
    expect(fitWithin(3024, 4032, 512)).toEqual({ width: 384, height: 512 })
  })
})

describe("renameForType", () => {
  it("바뀐 형식에 맞게 확장자를 바꾼다", () => {
    expect(renameForType("IMG_0001.HEIC", "image/jpeg")).toBe("IMG_0001.jpg")
    expect(renameForType("photo.png", "image/webp")).toBe("photo.webp")
    expect(renameForType("my.cat.photo.jpeg", "image/webp")).toBe("my.cat.photo.webp")
  })

  it("이름이 없으면 기본 이름을 쓴다", () => {
    expect(renameForType("", "image/jpeg")).toBe("image.jpg")
    expect(renameForType(undefined, "image/webp")).toBe("image.webp")
  })
})

describe("uploadErrorMessage", () => {
  it("413이면 용량 안내를 보여준다", () => {
    expect(uploadErrorMessage({ response: { status: 413 } }, "실패")).toContain("10.0MB")
  })

  it("서버 메시지가 있으면 그대로, 없으면 기본 문구", () => {
    expect(uploadErrorMessage({ response: { status: 400, data: { message: "적합하지 않은 파일입니다." } } }, "실패"))
      .toBe("적합하지 않은 파일입니다.")
    expect(uploadErrorMessage(new Error("network"), "실패")).toBe("실패")
  })
})

describe("compressImage", () => {
  it("GIF와 이미지가 아닌 파일은 원본을 돌려준다", async () => {
    const gif = new File(["x"], "a.gif", { type: "image/gif" })
    const txt = new File(["x"], "a.txt", { type: "text/plain" })
    expect(await compressImage(gif)).toBe(gif)
    expect(await compressImage(txt)).toBe(txt)
  })
})
