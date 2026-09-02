package net.atos.entng.support.services;

import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.services.impl.EscalationServiceZendeskImpl;
import net.atos.entng.support.zendesk.ZendeskIssue.ZendeskStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EscalationServiceZendeskImplTest {

    @Test
    public void waitingTicketWithOpenZendesk_staysWaiting() {
        assertEquals(
                TicketStatus.WAITING,
                EscalationServiceZendeskImpl.resolveSyncedStatus(TicketStatus.WAITING, ZendeskStatus.open)
        );
    }

    @Test
    public void waitingTicketWithSolvedZendesk_becomesResolved() {
        assertEquals(
                TicketStatus.RESOLVED,
                EscalationServiceZendeskImpl.resolveSyncedStatus(TicketStatus.WAITING, ZendeskStatus.solved)
        );
    }

    @Test
    public void openedTicketWithOpenZendesk_becomesOpened() {
        assertEquals(
                TicketStatus.OPENED,
                EscalationServiceZendeskImpl.resolveSyncedStatus(TicketStatus.OPENED, ZendeskStatus.open)
        );
    }

    @Test
    public void nonWaitingTicket_mapsEveryZendeskStatusToItsCorresponding() {
        for (ZendeskStatus z : ZendeskStatus.values()) {
            assertEquals(
                    "Zendesk " + z + " should map to its correspondingStatus",
                    z.correspondingStatus,
                    EscalationServiceZendeskImpl.resolveSyncedStatus(TicketStatus.OPENED, z)
            );
        }
    }

    @Test
    public void encodeFilename_withSimpleName_isUnchanged() {
        assertEquals("simple.pdf", EscalationServiceZendeskImpl.encodeFilename("simple.pdf"));
    }

    @Test
    public void encodeFilename_withSpaces_encodesAsPercent20() {
        assertEquals(
                "report%20final.pdf",
                EscalationServiceZendeskImpl.encodeFilename("report final.pdf")
        );
    }

    @Test
    public void encodeFilename_withReservedQueryCharacters_percentEncodesThem() {
        assertEquals(
                "invoice%262024%23draft.pdf",
                EscalationServiceZendeskImpl.encodeFilename("invoice&2024#draft.pdf")
        );
    }

    @Test
    public void encodeFilename_withPlusSign_encodesAsPercent2B() {
        assertEquals(
                "plus%2Bsign.txt",
                EscalationServiceZendeskImpl.encodeFilename("plus+sign.txt")
        );
    }
}