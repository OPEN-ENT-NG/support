import {ng, toasts} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload} from "../models/ticket.model";
import {ICanAccessResponse} from "../models/schoolWorkflow.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;

    exportSelectionCSV(ids: Array<number>): void;
    schoolWorkflow(userId: string, workflow: string, structureId: string): Promise<boolean>;
    countTicketsToExport(structureId: string): Promise<AxiosResponse>;
    directExport(structureId: string): void;
    workerExport(structureId: string): void;
    getConfig(): Promise<AxiosResponse>;
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
            .then((res: AxiosResponse) => (<ICanAccessResponse>res.data).canAccess),

    /**
     * count the number of ticket to export
     *
     * @param structureId {string} id of the structure
     * @returns {Promise<AxiosResponse>} number of ticket to export
     **/
    countTicketsToExport: (structureId: string): Promise<AxiosResponse> =>
        http.get(`/support/tickets/export/count/${structureId}`),

    /**
     * create and directly download the csv
     *
     * @param structureId {string} id of the structure
     * @returns {void}
     **/
    directExport: (structureId: string): void => {
        window.open(`/support/tickets/export/direct/${structureId}`)
    },

    /**
     * use worker to create the csv and download it in the workspace
     *
     * @param structureId {string} id of the structure
     * @returns {void}
     **/
    workerExport: (structureId: string): void => {
        http.get(`/support/tickets/export/worker/${structureId}`)
    },

    getConfig: (): Promise<AxiosResponse> =>
        http.get(`/support/config/maxTickets`)
            .then((res: AxiosResponse) => res.data.max)



};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);