export type Ticket = {
    subject: string;
    description: string;
    category: string;
    school_id: string;
    status: number;
    newComment: string;
    attachments: [];
}


export interface IBodyTicket {
    subject: string;
    description: string;
    category: string;
    school_id: string;
    status: number;
    newComment: string;
    attachments: [];
}