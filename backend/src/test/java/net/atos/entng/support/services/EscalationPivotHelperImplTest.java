package net.atos.entng.support.services;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.helpers.EscalationPivotHelper;
import net.atos.entng.support.helpers.impl.EscalationPivotHelperImpl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EscalationPivotHelperImplTest {

    private EscalationPivotHelper escalationPivotHelper;

    @Before
    public void setUp() {
        this.escalationPivotHelper = new EscalationPivotHelperImpl();
    }

    @Test
    public void shouldReplaceUnixNewlinesWithBr() {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello\nWorld");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        assertEquals(1, result.size());
        assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello<br>World", result.getString(0));
    }

    @Test
    public void shouldReplaceWindowsNewlinesWithBr() {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello\r\nWorld");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        assertEquals(1, result.size());
        assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello<br>World", result.getString(0));
    }

    @Test
    public void shouldLeaveCommentUnchangedWhenNoNewline() {
        JsonArray issueComments = new JsonArray().add("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello World");
        JsonArray result = escalationPivotHelper.compareComments(new JsonArray(), issueComments);

        assertEquals(1, result.size());
        assertEquals("20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello World", result.getString(0));
    }

    @Test
    public void shouldNotAddAlreadyExistingComment() {
        final String rawComment = "20260602102304 | Auteur du commentaire | 2026-06-02 10:23:04 | Hello";
        JsonArray issueComments = new JsonArray().add(rawComment);
        JsonArray ticketComments = new JsonArray().add(new io.vertx.core.json.JsonObject()
                .put("created", "2026-06-02T10:23:04")
                .put("content", rawComment));

        JsonArray result = escalationPivotHelper.compareComments(ticketComments, issueComments);

        assertEquals(0, result.size());
    }

    // Serialized date parts depend on the JVM timezone, so serialized comments are checked on their content part only

    @Test
    public void shouldConvertHtmlCommentToPlainText() {
        String html = "<p>Madame,</p><p>J’ai réussi à supprimer mon message.</p><p>Le problème était bien lié à la signature.</p>"
                + "<p>Merci infiniment pour votre aide.</p><p>Stéphanie Delfino </p>";

        String result = serializeSingleComment(html);

        assertTrue(result.matches("[0-9]{14} \\| DELFINO STEPHANIE \\| [^|]+ \\| [^<>]*"));
        assertTrue(result.endsWith(" | Madame,\nJ’ai réussi à supprimer mon message.\nLe problème était bien lié à la signature."
                + "\nMerci infiniment pour votre aide.\nStéphanie Delfino"));
    }

    @Test
    public void shouldConvertLineBreaksAndDecodeHtmlEntities() {
        String result = serializeSingleComment("<p>Tom &amp; Jerry&nbsp;: 1 &lt; 2<br>fin</p>");

        assertTrue(result.endsWith(" | Tom & Jerry : 1 < 2\nfin"));
    }

    @Test
    public void shouldRemoveImagesAndAttachmentsBlockFromHtmlComment() {
        String result = serializeSingleComment("<p>Voir capture</p><img src=\"/workspace/document/abc\">"
                + "<div class=\"attachments\"><a href=\"/workspace/document/def\">fichier.pdf</a></div>");

        assertTrue(result.endsWith(" | Voir capture"));
    }

    @Test
    public void shouldSendIwsCommentWithBrAsPlainTextWithoutSerializingItAgain() {
        String result = serializeSingleComment(" 20260601202842 |<br> DE-MONTLAUR Mireille |<br> 2026-06-01 20:28:42 |<br><br> Bonsoir,<br>merci");

        assertEquals(" 20260601202842 |\n DE-MONTLAUR Mireille |\n 2026-06-01 20:28:42 |\n\n Bonsoir,\nmerci", result);
    }

    @Test
    public void shouldLeavePlainTextCommentUnchanged() {
        String result = serializeSingleComment("Hello\n3 < 5 et 6 > 4");

        assertTrue(result.endsWith(" | Hello\n3 < 5 et 6 > 4"));
    }

    private String serializeSingleComment(String content) {
        JsonArray comments = new JsonArray().add(new JsonObject()
                .put("id", 1L)
                .put("content", content)
                .put("owner_name", "DELFINO STEPHANIE")
                .put("created", "2026-06-09T05:49:05"));
        return escalationPivotHelper.serializeComments(comments).getString(0);
    }
}