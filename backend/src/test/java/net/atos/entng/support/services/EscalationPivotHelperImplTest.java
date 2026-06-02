package net.atos.entng.support.services;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.helpers.EscalationPivotHelper;
import net.atos.entng.support.helpers.impl.EscalationPivotHelperImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EscalationPivotHelperImplTest {

    private EscalationPivotHelper escalationPivotHelper;

    @Before
    public void setUp() {
        this.escalationPivotHelper = new EscalationPivotHelperImpl();
    }

    @Test
    public void shouldReplaceUnixNewlinesWithBr(TestContext context) {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello\nWorld");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        context.assertEquals(1, result.size());
        context.assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello<br>World", result.getString(0));
    }

    @Test
    public void shouldReplaceWindowsNewlinesWithBr(TestContext context) {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello\r\nWorld");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        context.assertEquals(1, result.size());
        context.assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello<br>World", result.getString(0));
    }

    @Test
    public void shouldLeaveCommentUnchangedWhenNoNewline(TestContext context) {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello World");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        context.assertEquals(1, result.size());
        context.assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello World", result.getString(0));
    }

    @Test
    public void shouldNotAddAlreadyExistingComment(TestContext context) {
        final String rawComment = "20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello";
        JsonArray issueComments = new JsonArray().add(rawComment);
        JsonArray ticketComments = new JsonArray().add(new io.vertx.core.json.JsonObject()
                .put("created", "2026-06-02T10:23:04")
                .put("content", rawComment));

        JsonArray result = escalationPivotHelper.compareComments(ticketComments, issueComments);

        context.assertEquals(0, result.size());
    }
}