import {Controller, ng, idiom as lang, template, _, angular, notify, moment, Behaviours} from "entcore";
import { models } from "../models/model";

declare let model: any;

export const SupportController: Controller = ng.controller('SupportController',
	['$scope', 'route', 'orderByFilter', '$timeout', function ($scope, route, orderByFilter, $timeout) {
		route({
			displayTicket: function (params) {
				$scope.searchTicketNumber(params.ticketId);
			},
			listTickets: function() {
				if($scope.userIsLocalAdmin()) {
					$scope.registerViewTicketListEvent();
				} else {
					$scope.newTicket();
				}
			}
		});

		this.initialize = function() {
			let status;
			$scope.lang = lang;
			$scope.template = template;
			$scope.me = model.me;

			$scope.tickets = model.tickets;
			$scope.events = model.events;

			// but-tracker management : direct communication between user and bt ?
			model.isBugTrackerCommDirect(function(result){
				if(result && typeof result.isBugTrackerCommDirect === 'boolean') {
					$scope.isBugTrackerCommDirect = result.isBugTrackerCommDirect;
				}
			});

			// Categories
			let apps = _.filter(model.me.apps, function (app) {
				return app.address && app.name && app.address.length > 0 && app.name.length > 0;
			});
			apps = _.map(apps, function(app){
				app.displayName = lang.translate(app.displayName);
				return app;
			});
			// Add category "Other"
			const categoryOther: any = {address: 'support.category.other'};
			categoryOther.displayName = lang.translate(categoryOther.address);
			apps.push(categoryOther);

			$scope.apps = orderByFilter(apps, 'name');

			$scope.notFound = false;

			$scope.sort = {
				expression : 'modified',
				reverse : true
			};

			$scope.filter = {
				status : undefined
			};

			// Clone status enum and add i18n value
			const statusEnum = JSON.parse(JSON.stringify(model.ticketStatusEnum));
			for (status in statusEnum.properties) {
				if (statusEnum.properties.hasOwnProperty(status)) {
					statusEnum.properties[status].i18nValue = $scope.getStatusLabel(statusEnum.properties[status].value);
				}
			}
			$scope.statuses = statusEnum.properties;

			$scope.escalationStatuses = model.escalationStatuses;

			model.isEscalationActivated(function(result){
				if(result && typeof result.isEscalationActivated === 'boolean') {
					$scope.isEscalationActivated = result.isEscalationActivated;
				}
			});

			// Get user's Schools to build filter
			$scope.schools = [];
			model.getAdministeredStructures(function(result){
				if( angular.isArray(result) && result.length > 0 ) {
					// Use structures which user has admin access on.
					for (let i=0; i < result.length; i++) {
						$scope.schools.push({id: result[i].id, name: result[i].name});
					}
				} else {
					// Default to user's school
					for (let i=0; i < model.me.structures.length; i++) {
						$scope.schools.push({id: model.me.structures[i], name: model.me.structureNames[i]});
					}
				}
			});

			// filters initalization
			$scope.display = {};
			$scope.display.filters = [];
			$scope.display.histo = false;

			for (status in statusEnum.properties){
				$scope.display.filters[statusEnum.properties[status].value] = true;
			}
			$scope.display.filters.all = true;
			$scope.display.filters.mydemands = true;
			$scope.display.filters.otherdemands = true;

			$scope.display.filters.school_id ="*";

			$scope.display.filters.ticket_id ="";

			$scope.switchAll = function(){
				for(var filter in $scope.display.filters){
					if (filter !== "school_id" && filter !== "ticket_id") {
						$scope.display.filters[filter] = $scope.display.filters.all;
					}
				}
				if($scope.userIsLocalAdmin()) {
					// no need to update if the checkbox isn't visible.
					$scope.display.filters.mydemands = $scope.display.filters.all;
				}
				$scope.goPage(1, true);
			};

			$scope.checkAll = function(){
				$scope.display.filters.all = true;
				for(var filter in $scope.display.filters){
					$scope.display.filters.all = $scope.display.filters[filter] &&
						$scope.display.filters.mydemands &&
						$scope.display.filters.all;
				}
			};

			$scope.onStatusChange = function () {
				$scope.checkAll();
				$scope.goPage(1, true);
			};

			// Pagination system
			$scope.pages = [1];
			$scope.currentPage = 1;
			$scope.nbPages = 1;
			$scope.nbResultsTot = 1;
			$scope.nbTicketsPerPage = undefined; // Defined in TicketServiceSqlImpl.java and updated here accordingly
		};

		// Sort
		$scope.sortCategoryFunction = function(ticket) {
			return $scope.getCategoryLabel(ticket.category);
		};

		$scope.switchSortBy = function(expression) {
			if (expression === $scope.sort.expression) {
				$scope.sort.reverse = ! $scope.sort.reverse;
			}
			else {
				$scope.sort.expression = expression;
				$scope.sort.reverse = false;
			}
			$scope.goPage(1, true);
		};

		// View tickets
		$scope.displayTicketList = function() {
			$scope.notFound = false;
			$scope.display.histo = false;
			$scope.ticket = new models.Ticket();
			$scope.display.filters.all = true;
			$scope.switchAll();
			$scope.goPage(1, true);
		};

		$scope.registerViewTicketListEvent = function() {
			model.tickets.one('sync', function() {
				if ($scope.nbTicketsPerPage === undefined) { // init nbTicketsPerPage according to the number of results (given by config)
					$scope.nbTicketsPerPage = $scope.tickets.all.length;
				}
				$scope.updatePagination();
				window.location.hash = '';
				template.open('main', 'list-tickets');
				template.open('filters', 'filters');
			});
		};

		$scope.atLeastOneTicketEscalated = function() {
			return _.some(model.tickets.all, function(ticket){
				return ticket.last_issue_update !== null && ticket.last_issue_update !== undefined;
			});
		};

		$scope.inputSearchTicketNumber = function (ticketId) {
			$scope.searchInput = true;
			$scope.notFoundSearchInput = false;
			$scope.searchTicketNumber(ticketId);
		};

		$scope.setNotFoundFalse = function () {
			$scope.notFoundSearchInput = false;
		};

		$scope.searchTicketNumber = function (ticketId) {
			let hasTicket = [];
			const id = parseInt(ticketId, 10);
			if(!isNaN(id)){
				if(model.tickets.all)
					hasTicket = model.tickets.all.filter(ticket => {
						return ticket.id === id;
					});
				if (hasTicket.length === 0){
					model.getTicket(ticketId, function(result) {
						$scope.ticket = new models.Ticket(result[0]);
						$scope.openTicket(id);
					});
				}else{
					$scope.ticket = hasTicket[0];
					$scope.openTicket(id);
				}
			}else{
				notify.info('support.ticket.search.warning');
			}
		};

		$scope.openTicket = function(id) {
			if(!$scope.ticket || ($scope.ticket && $scope.ticket.id !== id))
				$scope.ticket = _.find(model.tickets.all, function(ticket){
					return ticket.id === id;
				});
			if(!$scope.ticket) {
				if($scope.searchInput){
					$scope.notFoundSearchInput = true;
					$scope.searchInput = false;
				}else{
					$scope.notFound = true;
				}
				$scope.$apply();
				return;
			}
			template.open('main', 'view-ticket');
			$scope.ticket.getAttachments();
			$scope.ticket.getComments(function() {
				$scope.initHisto(id.toString());
			});
			model.getProfile($scope.ticket.owner, function(result) {
				$scope.ticket.profile = result.profile;
			});
			$scope.ticket.getBugTrackerIssue();
		};

		$scope.viewTicket = function(ticketId) {
			window.location.hash = '/ticket/' + ticketId;
		};

		// called when opening ticket and updating
		$scope.initHisto = function(ticketId) {
			model.getEvents(ticketId, function(result) {
				template.open('histo-ticket', 'histo-ticket');
				$scope.events = result;

				$scope.events.forEach(function (event){
					const comment: any = {};
					comment.owner = event.user_id;
					comment.owner_name = event.username;
					comment.created = event.event_date;
					comment.content = event.event;
					comment.status = $scope.getStatusLabel(event.status);
					comment.isHistory = true;
					comment.type = event.event_type;
					$scope.ticket.comments.push(comment);
				});

				// adding the comments from bug tracker.
				if( $scope.isBugTrackerCommDirect ){
					if( $scope.ticket.issue && $scope.ticket.issue.journals && $scope.ticket.issue.journals.length > 0 ) {
						//get the bug tracker author name
						$scope.bugTrackerAuthor = $scope.ticket.issue.author.name;
						$scope.ticket.issue.journals.forEach( function( btComment) {
							if( btComment.notes !== "" ) {
								//alert(btComment.notes);
								// alert(btComment.created_on);
								if(btComment.user.name !== $scope.bugTrackerAuthor){
									let comment: any = {};
									comment.created = btComment.created_on;
									comment.content = btComment.notes;
									comment.type = 5;
									comment.isHistory = true;
									comment.owner_name = btComment.user.name;
									$scope.ticket.comments.push(comment);
								}
							}
						});
					}
				}

				$scope.ticketHisto = ticketId;
				$scope.$apply();
				//$scope.ticketLib = ticket.subject;
			}, function (e) {
				$scope.processingData = false;
				// validationError(e);
			});
		};

		// called when opening history view
		$scope.showHisto = function(ticket, ticketId) {
			model.getEvents(ticketId, function(result) {
				template.open('histo-ticket', 'histo-ticket');
				$scope.display.histo = true;
				$scope.events = result;
				$scope.ticketHisto = ticketId;
				$scope.ticketLib = ticket.subject;
				$scope.$apply();
			}, function (e) {
				$scope.processingData = false;
				// validationError(e);
			});
		};

		$scope.backHisto = function() {
			$scope.display.histo = false;
		};


		$scope.openViewTicketTemplate = function() {
			template.open('main', 'view-ticket');
		};

		// Create ticket
		$scope.newTicket = function() {
			$scope.ticket = new models.Ticket();
			template.open('main', 'create-ticket');
		};

		let hasDuplicateInNewAttachments = function () {
			// each attachmentId must appear only once. Return true if there are duplicates, false otherwise
			return _.chain($scope.ticket.newAttachments)
				.countBy(function (attachment) {
					return attachment._id;
				})
				.values()
				.some(function (count) {
					return count > 1;
				})
				.value();
		};

		$scope.createTicket = function(){
			$scope.ticket.event_count = 1;
			$scope.ticket.processing = true;

			// Hack: var thisTicket created to retain reference to $scope.ticket when ajax calls return, after it is setted to undefined
			const thisTicket = $scope.ticket;

			$scope.checkUpdateTicket($scope.ticket);
			if(!$scope.ticket.processing){
				return;
			}

			$scope.createProtectedCopies($scope.ticket, true, function() {
				$scope.ticket.id=null;
				$scope.ticket.processing = false;
				$scope.ticket.createTicket($scope.ticket, function() {
					// adding profile after creation
					model.getProfile($scope.me.userId, function(result) {
						thisTicket.profile = result.profile;
						thisTicket.newAttachments = [];
						notify.info('support.ticket.has.been.created');
						template.open('main', 'list-tickets');
						template.open('filters', 'filters');
						$scope.escalateTicketNow(thisTicket);
					});

				});
				$scope.ticket = undefined;
			});
		}.bind(this);

		$scope.cancelCreateTicket = function() {
			window.location.hash = '/list-tickets/';
			$scope.displayTicketList();
		};

		/*
         * Create a "protected" copy for each "non protected" attachment.
         * ("Non protected" attachments cannot be seen by everybody, whereas "protected" attachments can)
         */
		$scope.createProtectedCopies = function(pTicket, pIsCreateTicket, pCallback) {

			if(!pTicket.newAttachments || pTicket.newAttachments.length === 0) {
				if(typeof pCallback === 'function'){
					pCallback();
				}
			}
			else {
				const nonProtectedAttachments = _.filter(pTicket.newAttachments,
					function (attachment) {
						return attachment.protected !== true;
					});
				let remainingAttachments = nonProtectedAttachments.length;

				// Function factory, to ensure anAttachment has the proper value
				const makeCallbackFunction = function (pAttachment) {
					const anAttachment = pAttachment;
					return function (result) {
						// Exemple of result : {_id: "db1f060a-5c0e-45fa-8318-2d8b33873747", status: "ok"}

						/// NOTE 2020-11-09 :
						// i never get a document with 'status: "ok"' here, so i instead test the value of _id.
						// TODO remove commented code and explanations if correction accepted.
						if (result && typeof result._id === "string" /*&& result.status === "ok"*/) {
							console.log("createProtectedCopy OK for attachment " + anAttachment._id + ". Id of protected copy is:" + result._id);
							remainingAttachments = remainingAttachments - 1;

							// replace id of "non protected" attachment by the id of its "protected copy"
							pTicket.newAttachments = _.map(pTicket.newAttachments,
								function (attachment) {
									if (anAttachment._id === attachment._id) {
										attachment._id = result._id;
									}
									return attachment;
								}
							);
							if (remainingAttachments === 0) {
								console.log("createProtectedCopy OK for all attachments");
								if (typeof pCallback === 'function') {
									pCallback();
								}
							}
						} else {
							if (pIsCreateTicket === true) {
								notify.error('support.attachment.processing.failed.ticket.cannot.be.created');
								pTicket.processing = false;
							} else {
								notify.error('support.attachment.processing.failed.ticket.cannot.be.updated');
								pTicket.processing = false;
							}
						}
					};
				};

				if(nonProtectedAttachments && nonProtectedAttachments.length > 0) {
					for (var i=0; i < nonProtectedAttachments.length; i++) {
						Behaviours.applicationsBehaviours.workspace.protectedDuplicate(nonProtectedAttachments[i],
							makeCallbackFunction(nonProtectedAttachments[i]));
					}
				}
				else {
					if(typeof pCallback === 'function'){
						pCallback();
					}
				}
			}
		};

		// Update ticket
		$scope.editTicket = function() {
			$scope.editedTicket = $scope.ticket;
			template.open('main', 'edit-ticket');
		};

		$scope.checkUpdateTicket = function (ticket) {
			ticket.processing = true;

			if (!ticket.subject || ticket.subject.trim().length === 0){
				notify.error('support.ticket.validation.error.subject.is.empty');
				ticket.processing = false;
			}
			if( ticket.subject.length > 255) {
				notify.error('support.ticket.validation.error.subject.too.long');
				ticket.processing = false;
			}
			if (!ticket.description || ticket.description.trim().length === 0){
				notify.error('support.ticket.validation.error.description.is.empty');
				ticket.processing = false;
			}
			if(hasDuplicateInNewAttachments()) {
				notify.error('support.ticket.validation.error.duplicate.in.new.attachments');
				ticket.processing = false;
			}
		};

		$scope.updateTicket = function(){
			$scope.checkUpdateTicket($scope.editedTicket);
			if(!$scope.editedTicket.processing){
				return;
			}

			// check that the "new" attachments have not already been saved for the current ticket
			if($scope.ticket.newAttachments && $scope.ticket.newAttachments.length > 0) {
				const attachmentsIds = $scope.ticket.attachments.pluck('document_id');
				const newAttachmentsInDuplicate = [];

				for (let i=0; i < $scope.ticket.newAttachments.length; i++) {
					if(_.contains(attachmentsIds, $scope.ticket.newAttachments[i]._id)) {
						newAttachmentsInDuplicate.push({
							id: $scope.ticket.newAttachments[i]._id,
							name: $scope.ticket.newAttachments[i].title
						});
					}
				}

				if(newAttachmentsInDuplicate.length > 0) {
					if(newAttachmentsInDuplicate.length === 1) {
						notify.error(lang.translate('support.ticket.validation.error.attachment') +
							newAttachmentsInDuplicate[0].name +
							lang.translate('support.ticket.validation.error.already.linked.to.ticket'));
					}
					else {
						notify.error(lang.translate('support.ticket.validation.error.attachments.already.linked.to.ticket') +
							_.pluck(newAttachmentsInDuplicate,'name').join(", "));
					}
					$scope.editedTicket.processing = false;
					return;
				}
			}

			$scope.createProtectedCopies($scope.editedTicket, false, function() {
				$scope.ticket = $scope.editedTicket;

				$scope.ticket.updateTicket($scope.ticket, function() {
					if($scope.ticket.newAttachments && $scope.ticket.newAttachments.length > 0) {
						$scope.ticket.getAttachments();
						if ($scope.canEscalate($scope.ticket)) {
							$scope.ticket.getBugTrackerIssue();
						}
					}
					$scope.ticket.newAttachments = [];

					// clean the collection and refill (either comment or histo can be added).
					$scope.ticket.comments.all = [];

					$scope.ticket.getComments(function() {
						$scope.initHisto($scope.ticket.id);
					});

					$scope.ticket.newComment = '';
					$scope.ticket.processing = false;
					template.open('main', 'view-ticket');
				});

			});

		}.bind(this);

		$scope.cancelEditTicket = function() {
			$scope.editedTicket = new models.Ticket();
			template.open('main', 'view-ticket');
		};

		$scope.isCreatingOrEditingOrViewingEscalatedTicket = function() {
			return (template.contains('main', 'create-ticket') ||
				template.contains('main', 'edit-ticket') ||
				$scope.isViewingEscalatedTicket());
		};

		$scope.isCreating = function() {
			return (template.contains('main', 'create-ticket'));
		};

		$scope.isViewingEscalatedTicket = function() {
			return template.contains('main', 'view-bugtracker-issue');
		};

		// Functions to escalate tickets or process escalated tickets
		$scope.escalateTicket = function() {
			$scope.ticket.escalation_status = model.escalationStatuses.IN_PROGRESS;
			if($scope.ticket.status !== model.ticketStatusEnum.NEW && $scope.ticket.status !== model.ticketStatusEnum.OPENED) {
				notify.error('support.ticket.escalation.not.allowed.for.given.status');
				$scope.ticket.escalation_status = model.escalationStatuses.NOT_DONE;
				return;
			}

			const successCallback = function () {
				notify.info('support.ticket.escalation.successful');
			};
			const e500Callback = function () {
				notify.error('support.ticket.escalation.failed');
			};
			const e400Callback = function (result) {
				if (result && result.error) {
					notify.error(result.error);
				} else {
					notify.error('support.error.escalation.conflict');
				}
			};

			$scope.ticket.escalateTicket(successCallback, e500Callback, e400Callback);
			$scope.ticket.status = 2;
		};

		$scope.allTicketsUnescalatedAndCanEscalate = function() {
			return $scope.tickets.selection().every(function(e){return e.escalation_status === model.escalationStatuses.NOT_DONE && $scope.canEscalate(e)})
		};

		$scope.atLeastOneTicketUnescalated = function() {
			return $scope.tickets.selection().find(function(e){return e.escalation_status === model.escalationStatuses.NOT_DONE}) !== undefined
		};

		$scope.escalateSelected = function() {
			$scope.tickets.selection().forEach(function(element) {
				const id = parseInt(element.id, 10);
				$scope.ticket = _.find(model.tickets.all, function(ticket){
					return ticket.id === id;
				});
				if($scope.ticket && $scope.ticket.escalation_status === model.escalationStatuses.NOT_DONE) {
					$scope.escalateTicket();
				}
			});
			$scope.ticket = undefined;
		};

		$scope.createAndEscalateTicket = function(){
			$scope.escalateAfterCreation = true;
			$scope.createTicket();
		};

		$scope.escalateTicketNow = function(thisTicket) {
			if($scope.escalateAfterCreation){
				$scope.ticket = thisTicket;
				$scope.escalateTicket();
				$scope.escalateAfterCreation = false;
				$scope.ticket = undefined;
			}
		};

		$scope.openBugTrackerIssue = function() {
			template.open('main', 'view-bugtracker-issue');
		};

		$scope.editIssue = function() {
			$scope.ticket.issue.showEditForm = true;
		};

		$scope.cancelEditIssue = function() {
			$scope.ticket.issue.showEditForm = false;
			$scope.ticket.issue.newComment = '';
		};

		$scope.updateIssue = function() {
			$scope.ticket.issue.processing = true;

			if (!$scope.ticket.issue.newComment || $scope.ticket.issue.newComment.trim().length === 0){
				notify.error('support.issue.validation.error.comment.is.empty');
				$scope.ticket.issue.processing = false;
				return;
			}

			const successCallback = function () {
				$scope.ticket.issue.showEditForm = false;
				$scope.ticket.issue.processing = false;
				notify.info('support.comment.issue.successful');
			};

			const errorCallback = function (error) {
				notify.error(error);
				$scope.ticket.issue.processing = false;
			};

			$scope.ticket.commentIssue(successCallback, errorCallback);
		};

		// Date functions
		$scope.formatDate = function(date) {
			if(/^.*Z.*$/.test(date)){
				// If Z (=> UTC) is specified, moment will convert to UTC automatically
				return $scope.formatMoment(moment(date));
			}
			// Else, assuming date is in UTC, we convert to UTC by ourselves
			return $scope.formatMoment(moment.utc(date).local());
		};

		$scope.formatMoment = function(moment) {
			return moment.lang('fr').format('DD/MM/YYYY H:mm');
		};

		// Functions to display proper label
		$scope.getStatusLabel = function(status) {
			if(model.ticketStatusEnum.properties[status] !== undefined) {
				const key = model.ticketStatusEnum.properties[status].i18n;
				return lang.translate(key);
			}
			return undefined;
		};

		$scope.getCategoryLabel = function(appAddress) {
			const app = _.find($scope.apps, function (app) {
				return app.address === appAddress;
			});
			return (app !== undefined) ? app.displayName : undefined;
		};

		$scope.getSchoolName = function(schoolId) {
			const school = _.find($scope.schools, function (school) {
				return school.id === schoolId;
			});
			return (school !== undefined) ? school.name : undefined;
		};

		$scope.canEscalate = function(ticket){
			const canEscalate = model.me.workflow.support.escalate || false;
			return ($scope.isEscalationActivated === true && canEscalate);
		};

		$scope.userIsLocalAdmin = function(ticket){
			// SUPER_ADMIN
			if( model.me.functions.SUPER_ADMIN ) {
				return true;
			}

			// ADMIN_LOCAL
			const isLocalAdmin = (model.me.functions &&
				model.me.functions.ADMIN_LOCAL && model.me.functions.ADMIN_LOCAL.scope);

			if(ticket && ticket.school_id) {
				// if parameter "ticket" is supplied, check that current user is local administrator for the ticket's school
				return isLocalAdmin && _.contains(model.me.functions.ADMIN_LOCAL.scope, ticket.school_id);
			}
			return isLocalAdmin;
		};
		/**
		 * Modification en masse du statut
		 * @param newStatus : nouveau statut
		 */
		$scope.updateStatus = function(newStatus){
			model.updateTicketStatus($scope.tickets.selection(), newStatus, function() {
				notify.info('support.comment.status.modification.successful');
				model.tickets.sync($scope.currentPage, $scope.display.filters, $scope.display.filters.school_id, $scope.sort.expression, $scope.sort.reverse, function(){
					$scope.$apply();
				});
			}, function (e) {
				$scope.processingData = false;
				// validationError(e);
			});
		};

		// Pagination system functions
		$scope.updatePagination = function() {
			$scope.pages = [];
			$scope.nbResultsTot = $scope.tickets.all.length > 0 ? $scope.tickets.all[0].total_results : 0;
			$scope.nbPages = Math.ceil($scope.nbResultsTot / $scope.nbTicketsPerPage);
			if(!$scope.nbPages)
				$scope.nbPages = 1;
			const interval = 2; // Number of page visible before and after the current page
			let start = 1;
			let end = $scope.nbPages;

			// Controller of 'pagination rolling' if we have too much pages to display
			if ($scope.nbPages > (2 * interval) + 1) {
				if ($scope.currentPage <= start + interval) { // When we are in first pages
					end = (2 * interval) + 1;
				}
				else if ($scope.currentPage >= $scope.nbPages - interval) { // When we are in last pages
					start = $scope.nbPages - (2 * interval);
					end = $scope.nbPages;
				}
				else { // Everything between
					start = $scope.currentPage - interval;
					end = $scope.currentPage + interval;
				}
			}

			for (let i = start; i <= end; i++) {
				$scope.pages.push(i);
			}
		};

		$scope.goPage = function(page, statusChanged = false) {
			if (page > $scope.nbPages) {
				$scope.currentPage = $scope.nbPages;
			}
			else if (page < 1) {
				$scope.currentPage = 1;
			}
			else {
				$scope.currentPage = page;
			}

			// At start or on status change
			if (statusChanged) {
				$scope.registerViewTicketListEvent();
				model.tickets.sync($scope.currentPage, $scope.display.filters, $scope.display.filters.school_id, $scope.sort.expression, $scope.sort.reverse);
			}
			else {
				model.tickets.sync($scope.currentPage, $scope.display.filters, $scope.display.filters.school_id, $scope.sort.expression, $scope.sort.reverse, function () {
					$scope.updatePagination();
					$scope.$apply();
				});
			}
		};

		this.initialize();
	}]);