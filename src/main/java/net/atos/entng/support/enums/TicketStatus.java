/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.support.enums;

public enum TicketStatus {
	NEW(1), OPENED(2), RESOLVED(3), CLOSED(4), WAITING(5);

	private final int status;

	TicketStatus(int pStatus) {
		this.status = pStatus;
	}
	public int status(){
		return status;
	}

	public static TicketStatus fromStatus(Integer status)
	{
		if(status == null)
			return null;

		for(TicketStatus ts: TicketStatus.values())
		  if(ts.status == status.intValue())
  		    return ts;

  		return null;
	}
}
