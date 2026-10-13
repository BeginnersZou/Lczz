export const PROGRESS_IMAGE_LIMIT = 30
export const PROGRESS_VIDEO_LIMIT = 9
export const PROGRESS_PICKER_BATCH = 9

export const isProgressVideo = file => file?.fileType === 'video'
  || String(file?.mimeType || '').startsWith('video/')
  || /\.(mp4|mov|m4v)$/i.test(file?.tempFilePath || file?.path || file?.localPath || '')

export function progressMediaCounts(files = []) {
  const videos = files.filter(isProgressVideo).length
  return { images: files.length - videos, videos }
}

export function progressMediaRemaining(files = [], type = 'image') {
  const counts = progressMediaCounts(files)
  return Math.max(0, type === 'video' ? PROGRESS_VIDEO_LIMIT - counts.videos : PROGRESS_IMAGE_LIMIT - counts.images)
}

export function progressMediaLimitMessage(files = []) {
  const counts = progressMediaCounts(files)
  if (counts.images > PROGRESS_IMAGE_LIMIT) return '每条施工进度最多30张图片'
  if (counts.videos > PROGRESS_VIDEO_LIMIT) return '每条施工进度最多9个视频'
  return ''
}

// Reserve space before uploads start, including queued and failed drafts.
export function acceptProgressMedia(existing, selected, type) {
  const remaining = progressMediaRemaining(existing, type)
  const matching = selected.filter(file => (isProgressVideo(file) ? 'video' : 'image') === type)
  return { accepted: matching.slice(0, remaining), overflow: matching.length > remaining }
}
