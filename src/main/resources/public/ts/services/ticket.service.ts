import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload, ITicketPayloadExportCSV, Ticket} from "../models/ticket.model";
import {downloadBlobHelper} from "../helpers/download-blob.helper";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;
    exportSelectionCSV(body: { ids: number[] }): Promise<void>;
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
     * @param body {number[]} list of ticket ids
     * @returns {Promise<void>}
     **/
    exportSelectionCSV: async (body: { ids: number[] }): Promise<void> => {
        // Generate CSV and store it in a blob
        let doc = await http.post(`/support/tickets/export`, {'ids': body});
        return downloadBlobHelper.downloadBlob(doc);
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);