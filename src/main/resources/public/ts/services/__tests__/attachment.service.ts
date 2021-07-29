// tricks to fake "mock" entcore ng class in order to use service
import {attachmentService} from "../attachment.service";

jest.mock('entcore', () => ({
    ng: {service: jest.fn()}
}))

import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';

describe('AttachmentService', () => {

    const ticketId: number = 78;
    const attachmentId: string = "attachmentId";

    it('should return data when API delete Attachment request is correctly called', done => {
        let mock = new MockAdapter(axios);
        const data = {response: true};
        mock.onDelete(`/support/ticket/${ticketId}/attachment/${attachmentId}`).reply(200, data);
        attachmentService.delete(ticketId, attachmentId).then(response => {
            expect(response.data).toEqual(data);
            done();
        });
    });


});