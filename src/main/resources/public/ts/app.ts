import {ng, routes} from 'entcore';
import {SupportController} from "./controllers/controller";


ng.controllers.push(SupportController);

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