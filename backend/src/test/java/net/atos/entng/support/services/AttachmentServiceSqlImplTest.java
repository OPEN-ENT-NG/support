package net.atos.entng.support.services;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.services.impl.AttachmentServiceSqlImpl;
import org.entcore.common.sql.Sql;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.mock;

@RunWith(VertxUnitRunner.class)
public class AttachmentServiceSqlImplTest {

  private Sql sql;

  private AttachmentService attachmentService;

  @Before
  public void setUp() {
    this.sql = mock(Sql.class);
    this.attachmentService = new AttachmentServiceSqlImpl(null);
    // sql is a field of SqlCrudService, not of the service itself: inject the mock with Whitebox
    Whitebox.setInternalState(attachmentService, "sql", sql);
  }

  @Test
  public void testCheckAttachmentExist(TestContext context) throws Exception {
    String ticketId = "ticketId";
    String attachmentId = "attachmentId";

    String expectedQuery =
        "SELECT a.* FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ? ORDER BY a.created";
    JsonArray expectedParams = new JsonArray().add(ticketId).add(attachmentId);

    Whitebox.invokeMethod(attachmentService, "checkAttachmentExist", ticketId, attachmentId);

    Mockito.verify(sql)
        .prepared(
            Mockito.eq(expectedQuery),
            Mockito.eq(expectedParams),
            Mockito.<Handler<Message<JsonObject>>>any());
  }

  @Test
  public void testdeleteAttachmentFromTicketSqlRequest_IsCorrectlyCalled(TestContext context)
      throws Exception {
    String ticketId = "ticketId";
    String attachmentId = "attachmentId";

    String expectedQuery =
        "DELETE FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ?";
    JsonArray expectedParams = new JsonArray().add(ticketId).add(attachmentId);

    Whitebox.invokeMethod(attachmentService, "deleteAttachmentFromTicket", ticketId, attachmentId);

    Mockito.verify(sql)
        .prepared(
            Mockito.eq(expectedQuery),
            Mockito.eq(expectedParams),
            Mockito.<Handler<Message<JsonObject>>>any());
  }
}
