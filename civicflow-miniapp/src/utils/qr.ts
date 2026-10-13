import qrcode from 'qrcode-generator'
// Encoder only: no DOM, image URLs, or browser canvas implementation.
export function qrMatrix(value: string) {
  const qr = qrcode(0, 'M')
  qr.addData(value, 'Byte')
  qr.make()
  return Array.from({ length: qr.getModuleCount() }, (_, row) =>
    Array.from({ length: qr.getModuleCount() }, (_, col) =>
      qr.isDark(row, col),
    ),
  )
}
export function drawQr(value: string, owner: object, width = 280) {
  const matrix = qrMatrix(value),
    count = matrix.length,
    unit = width / (count + 8)
  const ctx = uni.createCanvasContext('checkin-qr', owner)
  ctx.setFillStyle('#ffffff')
  ctx.fillRect(0, 0, width, width)
  ctx.setFillStyle('#12263f')
  matrix.forEach((row, y) =>
    row.forEach((dark, x) => {
      if (dark)
        ctx.fillRect(
          Math.floor((x + 4) * unit),
          Math.floor((y + 4) * unit),
          Math.ceil(unit),
          Math.ceil(unit),
        )
    }),
  )
  ctx.draw()
}
