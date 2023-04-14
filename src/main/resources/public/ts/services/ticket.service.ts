import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketResponse} from "../models/ticket.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketResponse): Promise<AxiosResponse>;
}

export const ticketService: ITicketService = {
    /**
     * update ticket informations
     *
     * @param ticketId  {number} ticket identifier
     * @param body  {ITicketResponse} ticket changes
     * @returns {Promise<AxiosResponse>} result Axios
     */
    update: async (ticketId: number, body: ITicketResponse): Promise<AxiosResponse> => {
        return http.put(`/support/ticket/${ticketId}`, body);
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);