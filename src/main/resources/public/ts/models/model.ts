// Enum based on the following article : https://stijndewitt.wordpress.com/2014/01/26/enums-in-javascript/

import {_, http} from "entcore";

declare let model: any;

model.ticketStatusEnum = {
	NEW: 1,
	OPENED: 2,
	RESOLVED: 3,
	ClOSED: 4,
	WAITING: 5,
	properties: {
		1: {i18n: "support.ticket.status.new", value: 1},
		2: {i18n: "support.ticket.status.opened", value: 2},
		3: {i18n: "support.ticket.status.resolved", value: 4},
		4: {i18n: "support.ticket.status.closed", value: 5},
		5: {i18n: "support.ticket.status.waiting", value: 3}
	}
};

if (Object.freeze) {
	Object.freeze(model.ticketStatusEnum);
}

// Constants for escalationStatus
model.escalationStatuses = {
	NOT_DONE: 1,
	IN_PROGRESS: 2,
	SUCCESSFUL: 3,
	FAILED: 4
};

model.events = [];

if (Object.freeze) {
	Object.freeze(model.escalationStatuses);
}

export let models: any = {};

models.Ticket = function (value?: any){
	this.collection(models.Comment);
	this.collection(models.Attachment);
}

models.Comment = function (){}

models.Attachment = function (){}


model.isEscalationActivated = function(callback){
	http().get('/support/escalation').done(function(result){
		if(typeof callback === 'function'){
			callback(result);
		}
	}.bind(this));
};

model.isRichEditorActivated = function(callback){
	http().get('/support/editor').done(function(result){
		if(typeof callback === 'function'){
			callback(result);
		}
	}.bind(this));
};

model.updateTicketStatus = function(itemArray, newStatus, cb, cbe){
	http().postJson('/support/ticketstatus/' + newStatus, {ids:model.getItemsIds(itemArray)}).done(function (result) {
		if(typeof cb === 'function'){
			cb();
		}
	}.bind(this));
}

model.isBugTrackerCommDirect = function(callback){
	http().get('/support/isBugTrackerCommDirect').done(function(result){
		if(typeof callback === 'function'){
			callback(result);
		}
	}.bind(this));
};


models.Ticket.prototype.createTicket = function(data, callback) {
	http().postJson('/support/ticket', data).done(function(result){
		this.updateData(result);
		model.tickets.push(this);
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

models.Ticket.prototype.updateTicket = function(data, callback) {
	http().putJson('/support/ticket/' + this.id, data).done(function(result){
		this.updateData(result);
		this.trigger('change');
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

models.Ticket.prototype.escalateTicket = function(callback, errorCallback, badRequestCallback) {
	http().postJson('/support/ticket/' + this.id + '/escalate', null, {requestName: 'escalation-request' })
		.done(function(result){
				this.last_issue_update = result.issue.updated_on;
				this.escalation_status = model.escalationStatuses.SUCCESSFUL;
				this.issue = result.issue;
				this.trigger('change');
				if(typeof callback === 'function'){
					callback();
				}
			}.bind(this)
		)
		.e500(function(){
				this.escalation_status = model.escalationStatuses.FAILED;
				this.trigger('change');
				if(typeof errorCallback === 'function'){
					errorCallback();
				}
			}.bind(this)
		)
		.e400(function(){
			this.escalation_status = model.escalationStatuses.NOT_DONE;
			this.trigger('change');
			if(typeof badRequestCallback === 'function'){
				badRequestCallback();
			}
		}.bind(this));
};

models.Ticket.prototype.toJSON = function() {
	const json = {
		subject: this.subject,
		description: this.description,
		category: this.category,
		school_id: this.school_id,
		status: undefined,
		newComment: undefined,
		attachments: undefined
	};
	if(this.status !== undefined) {
		json.status = this.status;
	}
	if(this.newComment !== undefined) {
		json.newComment = this.newComment;
	}
	if(this.newAttachments && this.newAttachments.length > 0) {
		json.attachments = [];
		for (var i=0; i < this.newAttachments.length; i++) {
			json.attachments.push({
				id: this.newAttachments[i]._id,
				name: this.newAttachments[i].title,
				size: this.newAttachments[i].metadata.size
			});
		}
	}

	return json;
};

models.Ticket.prototype.getComments = function(callback) {
	http().get('/support/ticket/' + this.id + '/comments').done(function(result){
		if(result.length > 0) {
			this.comments.load(result);
		}
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

models.Ticket.prototype.getAttachments = function(callback) {
	http().get('/support/ticket/' + this.id + '/attachments').done(function(result){
		if(result.length > 0) {
			this.attachments.load(result);
		}
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

models.Ticket.prototype.isAttachmentDuplicated = function(attachment) {
	var ret = false;
	this.attachments && this.attachments.forEach( function(att){
		if( att && att.name == attachment.filename && att.size == attachment.filesize )
			ret = true;
	});
	return ret;
}

models.Ticket.prototype.getBugTrackerIssue = function(callback) {
	http().get('/support/ticket/' + this.id + '/bugtrackerissue').done(function(result){
		if(result.length > 0 && result[0] && result[0].content) {
			// JSON type in PostgreSQL is sent as a JSON string. Parse it
			var content = JSON.parse(result[0].content);
			if(content && content.issue) {

				this.issue = content.issue;

				var attachments = JSON.parse(result[0].attachments);
				if(attachments && attachments.length > 0) {
					// add fields "document_id" and "gridfs_id" to each attachment in variable "content.issue"
					this.issue.attachments = _.map(this.issue.attachments, function(pAttachment) {
						var anAttachment = _.find(attachments, function(att) {
							return att.id === pAttachment.id;
						});
						pAttachment.document_id = anAttachment.document_id;
						pAttachment.gridfs_id = anAttachment.gridfs_id;
						return pAttachment;
					});
				}
			}
		}
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

models.Ticket.prototype.commentIssue = function(callback, errorCallback) {
	http().postJson('/support/issue/' + this.issue.id + '/comment', {content: this.issue.newComment})
		.done(function(result){
				this.issue = result.issue;
				this.trigger('change');
				if(typeof callback === 'function'){
					callback();
				}
			}.bind(this)
		)
		.e500(function(result){
				if(typeof errorCallback === 'function'){
					errorCallback(result.error);
				}
			}.bind(this)
		);
};

model.build = function() {
	model.me.workflow.load(['support']);
	this.makeModels(models);

	this.collection(models.Ticket, {
		sync : function(page= 1, filters: any = [true,true,true,true,true], school = '*', sortBy= 'modified', order = true, callback) {
			const queryParams = '?page=' + page
				+ (filters[1]?'&status=1':'')
				+ (filters[2]?'&status=2':'')
				+ (filters[3]?'&status=3':'')
				+ (filters[4]?'&status=4':'')
				+ (filters.mydemands?'&applicant=ME':'')
				+ (filters.otherdemands?'&applicant=OTHER':'')
				+ '&school=' + school
				+ '&sortBy=' + sortBy
				+ '&order=' + (order?'DESC':'ASC');
			http().get('/support/tickets' + queryParams).done(function (tickets) {
				this.load(tickets);
				if(typeof callback === 'function'){
					callback();
				}
			}.bind(this));
		},
		behaviours: 'support'
	});
};

model.getItemsIds = function (items) {
	const itemArray = [];
	items.forEach(function (item) {
		itemArray.push(item.id);
	});

	return itemArray;
};

model.getProfile = function(userId, callback) {
	http().get('/support/profile/' + userId).done(function(result) {
		if(typeof callback === 'function'){
			callback(result);
		}
	});
};

model.getEvents = function (ticketId, callback) {
	http().get('/support/events/' + ticketId).done(function(result){
		if(typeof callback === 'function'){
			callback(result);
		}
	});
};

model.getTicket = function (ticketId, callback) {
	http().get('/support/ticket/' + ticketId).done(function(result){
		if(typeof callback === 'function'){
			callback(result);
		}
	});
};

model.getAdministeredStructures = function(callback) {
	if(typeof callback === 'function'){
		http().get('/directory/structure/admin/list').done(function(data) {
			callback( data );
		}).error(function(err) {
			callback( [] );
		});
	}
};
