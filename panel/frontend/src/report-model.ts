export type Status = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'
export type Participant = { id: string; name: string }

export type ReportSummary = {
  id: string
  serverName: string
  category: string
  playerName: string
  descriptionSnippet: string
  status: Status
  priority: Priority
  assigneeName: string | null
  participants: Participant[]
  hasInventory: boolean
  createdAt: string
}

export type Page = { reports: ReportSummary[]; number: number; size: number; total: number }
export type Choice = { id: string; name: string }
export type Choices = { servers: Choice[]; assignees: Choice[] }

export const STATUSES: Status[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'DUPLICATE']
export const PRIORITIES: Priority[] = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL']
export const FILTER_KEYS = ['search', 'serverId', 'priority', 'assigneeId', 'category', 'status']

export const STATUS_ICONS: Record<Status, string> = {
  NEW: 'item/paper.png',
  IN_PROGRESS: 'item/clock_00.png',
  RESOLVED: 'item/emerald.png',
  REJECTED: 'item/barrier.png',
  DUPLICATE: 'item/amethyst_shard.png',
}

export const PRIORITY_ICONS: Record<Priority, string> = {
  LOW: 'item/wooden_sword.png',
  NORMAL: 'item/stone_sword.png',
  HIGH: 'item/diamond_sword.png',
  CRITICAL: 'item/netherite_sword.png',
}

export function filterQuery(params: URLSearchParams): string {
  const query = new URLSearchParams()
  for (const key of FILTER_KEYS) {
    const value = params.get(key)
    if (value) query.set(key, value)
  }
  return query.toString()
}
