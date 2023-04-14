import {Attachment} from "./Attachment";

export interface IBodyTicket {
    id: number;
    subject: string;
    description: string;
    category: string;
    school_id: string;
    status: number;
    newComment: string;
    attachments: [];
}

export class Ticket {
    private _id: number;
    private _subject: string;
    private _description: string;
    private _category: string;
    private _school_id: string;
    private _status: number;
    private _newComment: string;
    private _attachments: Attachment[];


    constructor() {
        this._id = null;
        this._subject = null;
        this._description = null;
        this._category = null;
        this._school_id = null;
        this._status = null;
        this._newComment = null;
        this._attachments = null;
    }

    build(data: IBodyTicket): Ticket {
        this._id = data.id;
        this._subject = data.subject;
        this._description = data.description;
        this._category = data.category;
        this._school_id = data.school_id;
        this._status = data.status;
        this._newComment = data.newComment;
        this._attachments = data.attachments;

        return this;
    }


    get id(): number {
        return this._id;
    }

    set id(value: number) {
        this._id = value;
    }

    get subject(): string {
        return this._subject;
    }

    set subject(value: string) {
        this._subject = value;
    }

    get description(): string {
        return this._description;
    }

    set description(value: string) {
        this._description = value;
    }

    get category(): string {
        return this._category;
    }

    set category(value: string) {
        this._category = value;
    }

    get school_id(): string {
        return this._school_id;
    }

    set school_id(value: string) {
        this._school_id = value;
    }

    get status(): number {
        return this._status;
    }

    set status(value: number) {
        this._status = value;
    }

    get newComment(): string {
        return this._newComment;
    }

    set newComment(value: string) {
        this._newComment = value;
    }

    get attachments(): Attachment[] {
        return this._attachments;
    }

    set attachments(value: Attachment[]) {
        this._attachments = value;
    }
}


