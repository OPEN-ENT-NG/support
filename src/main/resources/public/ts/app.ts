import {ng, routes} from 'entcore';
import {SupportController} from "./controllers/controller";
import {AttachmentService} from "./services/attachment.service";


ng.controllers.push(SupportController);
ng.services.push(AttachmentService);

/**
 * Support routes declaration
 */
routes.define(function($routeProvider){
    $routeProvider
        .when('/ticket/:ticketId', {
            action: 'displayTicket'
        })
        .when('/list-tickets', {
            action: 'listTickets'
        })
        .otherwise({
            redirectTo: '/list-tickets'
        });
});