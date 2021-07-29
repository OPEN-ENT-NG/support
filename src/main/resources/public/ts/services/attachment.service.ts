import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";

export interface IAttachmentService {
    delete(ticketId: number, attachmentId: string): Promise<AxiosResponse>;
}

export const attachmentService: IAttachmentService = {
    /**
     * delete attachment from a ticket
     *
     * @param ticketId      {number} ticket identifier
     * @param attachmentId  {String} attachment identifier
     * @returns {Promise<AxiosResponse>} result Axios
     */
    delete: async (ticketId: number, attachmentId: string): Promise<AxiosResponse> => {
        return http.delete(`/support/ticket/${ticketId}/attachment/${attachmentId}`)
    }
};
export const AttachmentService = ng.service('AttachmentService', (): IAttachmentService => attachmentService);