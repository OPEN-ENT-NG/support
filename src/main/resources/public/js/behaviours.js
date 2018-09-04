var supportBehaviours = {
	workflow: {
		createTicket: 'net.atos.entng.support.controllers.TicketController|createTicket',
		listMyTickets: 'net.atos.entng.support.controllers.TicketController|listUserTickets',
		listAllTickets: 'net.atos.entng.support.controllers.TicketController|listTickets',
		escalate: "net.atos.entng.support.controllers.TicketController|escalateTicket"
	}
};

Behaviours.register('support', {
	behaviours: supportBehaviours,
	workflow: function(){
		var workflow = { };
		var supportWorkflow = supportBehaviours.workflow;
		for(var prop in supportWorkflow){
			if(model.me.hasWorkflow(supportWorkflow[prop])){
				workflow[prop] = true;
			}
		}
		return workflow;
	}
});