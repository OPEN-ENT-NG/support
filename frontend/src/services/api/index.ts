import { odeServices } from '@edifice.io/client';
import {
  School,
  ApiTicket,
  TicketApiCode,
  TicketType,
  UserInfo,
  TicketEvent,
  TicketComment,
  CreateTicket,
  UpdateTicket,
  TicketAttachment,
  ApiAttachment,
  BugTrackerIssue,
} from '~/models';

const tickets_api_base_url = '/support';
const directory_api_base_url = '/directory/';
const auth_api_base_url = '/auth/oauth2';

export function getTickets(
  page: number,
  status: TicketApiCode[],
  type: TicketType,
  schools: string[],
): Promise<ApiTicket[]> {
  const body = {
    page: page,
    sortBy: 'modified',
    order: 'DESC',
    statuses: status.map((s) => String(s)),
    applicant: type === 'mine' ? 'ME' : type === 'other' ? 'OTHER' : undefined,
    schools: schools.length > 0 ? schools : ['*'],
  }

  return odeServices.http().post(`${tickets_api_base_url}/tickets`, body);
}

export function getTicketById(ticketId: string): Promise<ApiTicket[]> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/ticket/${ticketId}`, {});
}

export function getAttachmentById(ticketId: string): Promise<ApiAttachment[]> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/ticket/${ticketId}/attachments`, {});
}

export function getEventsById(ticketId: string): Promise<TicketEvent[]> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/events/${ticketId}`, {});
}

export function getCommentsById(ticketId: string): Promise<TicketComment[]> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/ticket/${ticketId}/comments`, {});
}

export function getUserProfile(userId: string): Promise<{ profile: string }> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/profile/${userId}`, {});
}

//Required to get the full list of categories
export function getUserInfo(): Promise<UserInfo> {
  return odeServices.http().get(`${auth_api_base_url}/userinfo`, {});
}

export function getSchools(): Promise<School[]> {
  return odeServices
    .http()
    .get(`${directory_api_base_url}structure/admin/list`, {});
}

export function exportTickets(ticketIds: string[]): void {
  const params = new URLSearchParams();
  ticketIds.forEach((id) => params.append('id', id));
  window.open(`${tickets_api_base_url}/tickets/export?${params.toString()}`);
}

export async function getThresholdDirectExportTickets(): Promise<number> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/config/thresholdDirectExportTickets`)
    .then((res: any) => res.threshold as number);
}

export async function getTicketsPerPage(): Promise<number> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/config/numberTicketsPerPage`)
    .then((res: { nbTicketsPerPage: number }) => res.nbTicketsPerPage);
}

export async function countTickets(structureId: string): Promise<number> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/structures/${structureId}/tickets/count`)
    .then((res: any) => res.count as number);
}

export function directExportTickets(structureId: string): void {
  window.open(`${tickets_api_base_url}/tickets/export/direct/${structureId}`);
}

export async function workerExportTickets(structureId: string): Promise<void> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/tickets/export/worker/${structureId}`);
}

export async function escalateTickets(ticketIds: string[]): Promise<any[]> {
  return Promise.all(
    ticketIds.map((id) =>
      odeServices
        .http()
        .post(`${tickets_api_base_url}/ticket/${id}/escalate`, {}),
    ),
  );
}

export async function refreshTicket(ticketId: string): Promise<void> {
  return odeServices
    .http()
    .post(`${tickets_api_base_url}/ticket/${ticketId}/refresh`);
}

export async function uploadAttachment(file: File): Promise<TicketAttachment> {
  const formData = new FormData();
  formData.append('file', file);
  return odeServices
    .http()
    .postFile<any>(
      '/workspace/document?protected=true&application=media-library&quality=1',
      formData,
    )
    .then((res) => ({
      id: res._id,
      name: res.metadata.filename,
      size: res.metadata.size,
    }));
}

export async function createTicket(
  ticket: Partial<CreateTicket>,
): Promise<ApiTicket> {
  return odeServices.http().post(`${tickets_api_base_url}/ticket`, ticket);
}

export async function updateTicket(
  ticketId: number,
  ticket: UpdateTicket,
): Promise<ApiTicket> {
  return odeServices
    .http()
    .put(`${tickets_api_base_url}/ticket/${ticketId}`, ticket);
}

export async function getWorkspaceDocumentProperties(
  documentId: string,
): Promise<{ _id: string; name: string }> {
  return odeServices.http().get(`/workspace/document/properties/${documentId}`);
}

export async function getBugTrackerIssueById(
  ticketId: string,
): Promise<BugTrackerIssue> {
  return odeServices
    .http()
    .get(`${tickets_api_base_url}/ticket/${ticketId}/bugtrackerissue`);
}
