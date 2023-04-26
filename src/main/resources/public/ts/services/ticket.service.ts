import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload} from "../models/ticket.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;
    exportTicketsCSV(body: ITicketPayload): void;
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
     * export ticket list as CSV format
     *
     **/
    exportTicketsCSV: (body: ITicketPayload): void => {
        let filterParams: string = '';
        if (body.school) {
            filterParams += `?school=${body.school}`
        }

        if (body.sortBy) {
            filterParams += `&sortBy=${body.sortBy}`
        }

        if (body.order) {
            filterParams += `&order=${body.order}`
        }

        let url: string = `/support/tickets/export` + filterParams;
        window.open(url);
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);