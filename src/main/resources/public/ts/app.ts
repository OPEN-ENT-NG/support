import {ng, routes} from 'entcore';
import {SupportController} from "./controllers/controller";
import * as directives from "./directives";
import * as services from "./services";


ng.controllers.push(SupportController);


for (let service in services) {
    ng.services.push(services[service]);
}

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