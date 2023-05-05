import {ng} from 'entcore'
import http, {AxiosResponse} from "axios";
import {ITicketPayload, ITicketPayloadExportCSV, Ticket} from "../models/ticket.model";

export interface ITicketService {
    update(ticketId: number, body: ITicketPayload): Promise<AxiosResponse>;
    exportTicketsCSV(body: ITicketPayloadExportCSV): void;
    exportSelectionCSV(body: number[]): Promise<void>;
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
    exportTicketsCSV: (body: ITicketPayloadExportCSV): void => {
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
    },

    exportSelectionCSV: async (body: number[]): Promise<void> => {
        // Generate CSV and store it in a blob
        let doc = await http.post(`/support/tickets/export`, {'ids': body});
        let blob = new Blob(["\ufeff" + doc.data], {type: 'text/csv; charset=utf-8'});

        // Download the blob
        let link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download =  doc.headers['content-disposition'].split('filename=')[1];
        link.click();
    }
};
export const TicketService = ng.service('TicketService', (): ITicketService => ticketService);