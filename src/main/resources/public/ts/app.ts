import {ng, routes} from 'entcore';
import {SupportController} from "./controllers/controller";
import {AttachmentService} from "./services/attachment.service";
import * as directives from "./directives";


ng.controllers.push(SupportController);
ng.services.push(AttachmentService);
for (let directive in directives) {
    ng.directives.push(directives[directive]);
}

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