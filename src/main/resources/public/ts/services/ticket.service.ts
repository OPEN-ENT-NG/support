import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload} from "../models/ticket.model";
import {ICanAccessResponse} from "../models/schoolWorkflow.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;

    exportSelectionCSV(ids: Array<number>): void;
    schoolWorkflow(userId: string, workflow: string, structureId: string): Promise<boolean>;
}

export const ticketService: ITicketService = {
    /**
     * update ticket informations
     *
     * @param ticketId  {number} ticket identifier
     * @param body  {ITicketPayload} ticket changes
     * @returns {Promise<AxiosResponse>} result Axios
     */
    update: async (ticketId: number, body: ITicketPayload): Promise<AxiosResponse> => {
        return http.put(`/support/ticket/${ticketId}`, body);
    },

    /**
     * export selected tickets as CSV format
     *
     * @param ids {Array<number>} list of ticket ids
     * @returns {void}
     **/
    exportSelectionCSV: (ids: Array<number>): void => {
        let urlParams: URLSearchParams = new URLSearchParams();
        ids.forEach((id: number) => urlParams.append('id', String(id)));
        window.open(`/support/tickets/export?${urlParams}`);
    },

    /**
     * check if the structure and the user have a certain workflow
     *
     * @param userId {string} id of the user
     * @param workflow {string} workflow wanted
     * @param structureId {string} id of the structure
     * @returns {Promise<AxiosResponse>} result Axios
     **/
    schoolWorkflow: (userId: string, workflow: string, structureId: string): Promise<boolean> =>
        http.get(`/support/check/user/${userId}/workflow/${workflow}/structure/${structureId}/auto/open`)
            .then((res: AxiosResponse) => (<ICanAccessResponse>res.data).canAccess)
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);