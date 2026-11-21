import { get, post } from './http'
import type {
  PageData,
  TaskCheckinResult,
  TaskCreatePayload,
  TaskStatus,
  TaskView,
  TaskType,
  VolunteerTaskView,
} from './types'

/** 志愿任务 5 接口（D3 P1）。 */

export function fetchTasks(params: {
  status?: TaskStatus | ''
  type?: TaskType | ''
  page?: number
  size?: number
}): Promise<PageData<TaskView>> {
  return get<PageData<TaskView>>('/tasks', params)
}

export function createTask(payload: TaskCreatePayload): Promise<TaskView> {
  return post<TaskView>('/tasks', payload)
}

export function joinTask(taskId: number): Promise<TaskView> {
  return post<TaskView>(`/tasks/${taskId}/join`)
}

export function checkinTask(taskId: number, photo: string): Promise<TaskCheckinResult> {
  return post<TaskCheckinResult>(`/tasks/${taskId}/checkin`, { photo })
}

export function fetchMyTasks(params: {
  page?: number
  size?: number
}): Promise<VolunteerTaskView> {
  return get<VolunteerTaskView>('/tasks/mine', params)
}
