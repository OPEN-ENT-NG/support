package net.atos.entng.support.services;

import io.vertx.core.json.JsonArray;
import net.atos.entng.support.services.impl.AttachmentServiceSqlImpl;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.sql.Sql;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.mock;

@RunWith(VertxUnitRunner.class)
public class AttachmentServiceSqlImplTest {

    Sql sql = mock(Sql.class);

    private AttachmentService attachmentService;

    @Before
    public void setUp(TestContext context) {
        this.attachmentService = new AttachmentServiceSqlImpl(null);
    }

    @Test
    public void testCheckAttachmentExist(TestContext context){
        // Arguments
        String ticketId = "ticketId";
        String attachmentId = "attachmentId";

        // Expected data
        String expectedQuery = "SELECT a.* FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ? ORDER BY a.created";
        JsonArray expectedParams = new JsonArray()
                .add(ticketId)
                .add(attachmentId);

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            JsonObject params = invocation.getArgument(1);
            context.assertEquals(query, expectedQuery);
            context.assertEquals(expectedParams, params);
            return null;
        }).when(sql).insert(Mockito.anyString(), Mockito.any(JsonObject.class), Mockito.any(Handler.class));

        try {
            Whitebox.invokeMethod(attachmentService, "checkAttachmentExist", ticketId, attachmentId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testdeleteAttachmentFromTicketSqlRequest_IsCorrectlyCalled(TestContext context){
        // Arguments
        String ticketId = "ticketId";
        String attachmentId = "attachmentId";

        // Expected data
        String expectedQuery = "DELETE FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ?";
        JsonArray expectedParams = new JsonArray()
                .add(ticketId)
                .add(attachmentId);

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            JsonObject params = invocation.getArgument(1);
            context.assertEquals(query, expectedQuery);
            context.assertEquals(expectedParams, params);
            return null;
        }).when(sql).insert(Mockito.anyString(), Mockito.any(JsonObject.class), Mockito.any(Handler.class));

        try {
            Whitebox.invokeMethod(attachmentService, "deleteAttachmentFromTicket", ticketId, attachmentId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}