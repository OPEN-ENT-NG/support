import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload} from "../models/ticket.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;

    exportSelectionCSV(ids: Array<number>): void;
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
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);