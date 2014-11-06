package net.atos.entng.support.controllers;

import static net.atos.entng.support.Support.SUPPORT_NAME;
import static net.atos.entng.support.TicketStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.TicketServiceSqlImpl;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.UserServiceNeoImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;


public class TicketController extends ControllerHelper {

	private static final String TICKET_CREATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_CREATED";
	private static final String TICKET_UPDATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_UPDATED";
	private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;
	private static final String DATE_FORMAT_FROM_POSTGRESQL_MOD = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	private static final String NOTIFICATION_DATE_FORMAT = "dd/MM/yy HH:mm";

	private TicketService ticketService;
	private UserService userService;

	public TicketController() {
		ticketService = new TicketServiceSqlImpl();
		userService = new UserServiceNeoImpl();
	}

	@Post("/ticket")
	@ApiDoc("Create a ticket")
	@SecuredAction("support.ticket.create")
	public void createTicket(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createTicket", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject ticket) {
							ticket.putNumber("status", NEW.status());
							ticketService.createTicket(ticket, user, getCreateOrUpdateTicketHandler(request, user, ticket, true));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	private Handler<Either<String, JsonObject>> getCreateOrUpdateTicketHandler(final HttpServerRequest request,
			final UserInfos user, final JsonObject ticket, final boolean isCreate) {

		return new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					JsonObject response = event.right().getValue();
					if (response != null && response.size() > 0) {
						if(isCreate) {
							notifyTicketCreated(request, user, response, ticket);
							response.putString("owner_name", user.getUsername());
						}
						else {
							notifyTicketUpdated(request, user, response);
						}
						renderJson(request, response, 200);
					} else {
						notFound(request);
					}
				} else {
					badRequest(request, event.left().getValue());
				}
			}
		};
	}

	/**
	 * Notify local administrators that a ticket has been created
	 */
	private void notifyTicketCreated(final HttpServerRequest request, final UserInfos user,
			final JsonObject response, final JsonObject ticket) {

		final String eventType = TICKET_CREATED_EVENT_TYPE;
		final String template = "notify-ticket-created.html";

		try {
			final long id = response.getLong("id", 0L);
			String date = response.getString("created", null);
			final String ticketSubject = ticket.getString("subject", null);

			if(id == 0L || date == null || ticketSubject == null) {
				log.error("Could not get parameters id, created or subject. Unable to send timeline "+ eventType
								+ " notification.");
				return;
			}
			final String ticketId = Long.toString(id);
			final String ticketDate = getFormattedDate(date);

			userService.getLocalAdministrators(user, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {
						Set<String> recipientSet = new HashSet<>();
						for (Object user : event.right().getValue()) {
							if(user instanceof JsonObject) {
								String userId = ((JsonObject) user).getString("userId");
								recipientSet.add(userId);
							}
						}

						List<String> recipients = new ArrayList<>(recipientSet);
						if(!recipients.isEmpty()) {
							JsonObject params = new JsonObject();
							params.putString("uri", container.config().getString("userbook-host") +
									"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
							params.putString("ticketUri", container.config().getString("host")
									+ "/support#/ticket/" + ticketId)
								.putString("username", user.getUsername())
								.putString("ticketid", ticketId)
								.putString("ticketdate", ticketDate)
								.putString("ticketsubject", shortenSubject(ticketSubject));

							notification.notifyTimeline(request, user, SUPPORT_NAME, eventType,
									recipients, ticketId, template, params);
						}

					} else {
						log.error("Unable to send timeline "+ eventType
								+ " notification. Error when calling service getLocalAdministrators : "
								+ event.left().getValue());
					}
				}
			});

		} catch (Exception e) {
			log.error("Unable to send timeline "+ eventType + " notification.", e);
		}
	}

	private String shortenSubject(String subject) {
		if(subject.length() > SUBJECT_LENGTH_IN_NOTIFICATION) {
			return subject.substring(0, SUBJECT_LENGTH_IN_NOTIFICATION)
					.concat(" [...]");
		}
		return subject;
	}

	// Format date for notifications
	private String getFormattedDate(String date) throws ParseException {
		DateFormat sourceFormat = new SimpleDateFormat(DATE_FORMAT_FROM_POSTGRESQL_MOD);
		DateFormat targetFormat = new SimpleDateFormat(NOTIFICATION_DATE_FORMAT);

		Date aDate = sourceFormat.parse(date);
		return targetFormat.format(aDate);
	}


	@Put("/ticket/:id")
	@ApiDoc("Update a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void updateTicket(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updateTicket", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject ticket) {
							// TODO : do not authorize description update if there is a comment
							ticketService.updateTicket(ticketId, ticket, user,
									getCreateOrUpdateTicketHandler(request, user, null, false));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	/**
	 * Notify owner and local administrators that the ticket has been updated
	 */
	private void notifyTicketUpdated(final HttpServerRequest request, final UserInfos user,
			final JsonObject response) {

		final String eventType = TICKET_UPDATED_EVENT_TYPE;
		final String template = "notify-ticket-updated.html";

		try {
			String date = response.getString("modified", null);
			final String ticketSubject = response.getString("subject", null);
			final String ticketOwner = response.getString("owner", null);
			final String ticketId = request.params().get("id");

			if(date == null || ticketSubject == null || ticketOwner == null) {
				log.error("Could not get parameters modified, subject or owner. Unable to send timeline "+ eventType
								+ " notification.");
				return;
			}
			final String ticketDate = getFormattedDate(date);

			final Set<String> recipientSet = new HashSet<>();
			if(!ticketOwner.equals(user.getUserId())) {
				recipientSet.add(ticketOwner);
			}

			userService.getLocalAdministrators(user, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {

						for (Object user : event.right().getValue()) {
							if(user instanceof JsonObject) {
								String userId = ((JsonObject) user).getString("userId");
								recipientSet.add(userId);
							}
						}
						List<String> recipients = new ArrayList<>(recipientSet);

						if(!recipients.isEmpty()) {
							JsonObject params = new JsonObject();
							params.putString("uri", container.config().getString("userbook-host") +
									"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
							params.putString("ticketUri", container.config().getString("host")
									+ "/support#/ticket/" + ticketId)
								.putString("username", user.getUsername())
								.putString("ticketid", ticketId)
								.putString("ticketdate", ticketDate)
								.putString("ticketsubject", shortenSubject(ticketSubject));

							notification.notifyTimeline(request, user, SUPPORT_NAME, eventType,
									recipients, ticketId, template, params);
						}

					} else {
						log.error("Unable to send timeline "+ eventType
								+ " notification. Error when calling service getLocalAdministrators : "
								+ event.left().getValue());
					}
				}
			});

		} catch (Exception e) {
			log.error("Unable to send timeline "+ eventType + " notification.", e);
		}
	}


	@Get("/tickets")
	@ApiDoc("If current user is local admin, get all tickets. Otherwise, get my tickets")
	@SecuredAction("support.ticket.list")
	public void listTickets(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					Map<String, UserInfos.Function> functions = user.getFunctions();
					if (functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
						ticketService.listTickets(user, arrayResponseHandler(request));
					}
					else {
						ticketService.listMyTickets(user, arrayResponseHandler(request));
					}
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});

	}

}
