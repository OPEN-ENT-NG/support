import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {IBodyTicket} from "../models/ticket.model";

export interface ITicketService {
    update(ticketId: number, body: IBodyTicket): Promise<AxiosResponse>;
}

export const ticketService: ITicketService = {
    /**
     * update ticket informations
     *
     * @param ticketId  {number} ticket identifier
     * @param body  {IBodyTicket} ticket changes
     * @returns {Promise<AxiosResponse>} result Axios
     */
    update: async (ticketId: number, body: IBodyTicket): Promise<AxiosResponse> => {
        return http.put(`/support/ticket/${ticketId}`, body);
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);