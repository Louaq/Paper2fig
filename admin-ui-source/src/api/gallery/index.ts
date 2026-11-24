import request from '@/config/axios'

export interface PageParam {
  pageNo: number
  pageSize: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// ---------------- 论文 ----------------
export const getPaperPage = (params: any) => request.get({ url: '/gallery/paper/page', params })
export const getPaper = (id: number) => request.get({ url: '/gallery/paper/get?id=' + id })
export const updatePaper = (data: any) => request.put({ url: '/gallery/paper/update', data })
export const uploadPaperPdf = (data: FormData) => request.upload({ url: '/gallery/paper/upload', data })
export const deletePaper = (id: number) => request.delete({ url: '/gallery/paper/delete?id=' + id })
export const deletePaperList = (ids: number[]) =>
  request.delete({ url: '/gallery/paper/delete-list', params: { ids: ids.join(',') } })
export const clearAllPapers = () => request.delete({ url: '/gallery/paper/all' })

// ---------------- 任务 ----------------
export const getJobPage = (params: any) => request.get({ url: '/gallery/job/page', params })
export const getJob = (id: number) => request.get({ url: '/gallery/job/get?id=' + id })
export const createCrawlJob = (data: any) => request.post({ url: '/gallery/job/create-crawl', data })
export const createCropJob = (data: any) => request.post({ url: '/gallery/job/create-crop', data })
export const createFetchJob = (data: any) => request.post({ url: '/gallery/job/create-fetch', data })
export const cancelJob = (id: number) => request.post({ url: '/gallery/job/cancel?id=' + id })
export const deleteJob = (id: number) => request.delete({ url: '/gallery/job/delete?id=' + id })
export const getJobLog = (id: number, lines = 300) =>
  request.get({ url: '/gallery/job/log', params: { id, lines } })

// ---------------- 审核 ----------------
export const getReviewPage = (params: any) => request.get({ url: '/gallery/review/page', params })
export const approveReview = (data: any) => request.post({ url: '/gallery/review/approve', data })
export const rejectReview = (data: any) => request.post({ url: '/gallery/review/reject', data })
export const batchApproveReview = (data: any) =>
  request.post({ url: '/gallery/review/batch-approve', data })
export const requeueReview = (data: any) => request.post({ url: '/gallery/review/requeue', data })
export const deleteReviewCrop = (id: number) =>
  request.delete({ url: '/gallery/review/delete-crop?id=' + id })

// ---------------- 发布 ----------------
export const getPublishPage = (params: any) => request.get({ url: '/gallery/publish/page', params })
export const getPublishSummary = () => request.get({ url: '/gallery/publish/summary' })
export const previewPublish = (force = false) =>
  request.post({ url: `/gallery/publish/preview?force=${force}` })
export const executePublish = (force = false) =>
  request.post({ url: `/gallery/publish/execute?force=${force}` })
export const cleanupPublishedArtifacts = (dryRun = false) =>
  request.post({ url: `/gallery/publish/cleanup-artifacts?dryRun=${dryRun}` })
export const syncSearchIndex = () => request.post({ url: '/gallery/publish/sync-search' })
export const deletePublishRecord = (id: number) =>
  request.delete({ url: '/gallery/publish/delete?id=' + id })

// ---------------- 参数 ----------------
export const getSettings = () => request.get({ url: '/gallery/setting/get' })
export const updateSettings = (values: Record<string, string>) =>
  request.put({ url: '/gallery/setting/update', data: { values } })

// ---------------- 静态站图片 ----------------
export const getFigurePage = (params: any) =>
  request.get({ url: '/gallery/figure/page', params })
export const getFigure = (id: number) => request.get({ url: '/gallery/figure/get?id=' + id })
export const updateFigure = (data: any) => request.put({ url: '/gallery/figure/update', data })
export const uploadFigureImage = (id: number, file: File) => {
  const data = new FormData()
  data.append('id', String(id))
  data.append('file', file)
  return request.upload({ url: '/gallery/figure/upload-image', data })
}
export const deleteFigure = (id: number) =>
  request.delete({ url: '/gallery/figure/delete?id=' + id })
export const deleteFigureList = (ids: number[]) =>
  request.delete({ url: '/gallery/figure/delete-list', params: { ids: ids.join(',') } })

// ---------------- 前端配置 ----------------
export const getSiteOptions = (dimension?: string) =>
  request.get({ url: '/gallery/site-config/list', params: dimension ? { dimension } : {} })
export const createSiteOption = (data: any) =>
  request.post({ url: '/gallery/site-config/create', data })
export const updateSiteOption = (data: any) =>
  request.put({ url: '/gallery/site-config/update', data })
export const deleteSiteOption = (id: number) =>
  request.delete({ url: '/gallery/site-config/delete?id=' + id })
export const sortSiteOptions = (ids: number[]) =>
  request.put({ url: '/gallery/site-config/sort', data: ids })

// ---------------- 看板 ----------------
export const getDashboard = () => request.get({ url: '/gallery/dashboard/stats' })
