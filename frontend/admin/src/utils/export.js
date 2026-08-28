function escapeCsvCell(value) {
  const text = value == null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

export function createCsvBlob(headers, rows) {
  const lines = [
    headers.map(item => escapeCsvCell(item.label)).join(','),
    ...rows.map(row => headers.map(item => escapeCsvCell(item.value(row))).join(','))
  ]
  return new Blob([`\uFEFF${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' })
}
