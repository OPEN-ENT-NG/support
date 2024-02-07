import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import {ticketService} from "../ticket.service";
import {ITicketPayload} from "../../models/ticket.model";

jest.mock('entcore', () => ({
    ng: {service: jest.fn()}
}))

describe('TicketService', () => {

    const ticketId: number = 78;
    const newStatus: ITicketPayload = {"status": 2};

    it('should return data when API put TicketStatus request is correctly called', done => {
        let mock = new MockAdapter(axios);
        const data = {response: true};
        mock.onPut(`/support/ticket/${ticketId}`, newStatus).reply(200, data);
        ticketService.update(ticketId, newStatus).then(response => {
            expect(response.data).toEqual(data);
            done();
        });
    });

});