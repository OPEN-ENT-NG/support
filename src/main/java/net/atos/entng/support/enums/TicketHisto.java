package net.atos.entng.support.enums;

public enum TicketHisto
{
    /* Old doc
    * @param eventType : 1 : new ticket /
    *                    2 : ticket updated /
    *                    3 : new comment /
    *                    4 : ticket escalated to bug-tracker /
    *                    5 : new comment from bug-tracker /
    *                    6 : bug-tracker updated.
    */

    NEW(1),
    UPDATED(2),
    COMMENT(3),
    ESCALATION(4),
    REMOTE_COMMENT(5),
    REMOTE_UPDATED(6);

	private final int eventType;

	TicketHisto(int eventType) {
		this.eventType = eventType;
	}
	public int eventType(){
		return eventType;
	}

	public static TicketHisto fromEventType(Integer eventType)
	{
		if(eventType == null)
			return null;

		for(TicketHisto th : TicketHisto.values())
		  if(th.eventType == eventType.intValue())
  		    return th;

  		return null;
	}
}
