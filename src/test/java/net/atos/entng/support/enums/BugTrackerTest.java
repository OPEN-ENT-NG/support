package net.atos.entng.support.enums;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.services.EscalationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.ParseException;

@RunWith(VertxUnitRunner.class)
public class BugTrackerTest {
    private Vertx vertx;
    private EscalationService escalationService;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();

    }

    @Test
    public void getIssueIdPivotTest(TestContext ctx) {
        Number issueIdExpected = 545;
        JsonObject issue = IssuePassed();
        Number issueIdResult = BugTracker.PIVOT.getIssueId(issue);
        ctx.assertEquals(issueIdResult, issueIdExpected);
    }

    @Test
    public void getIssueIdPivotFailedTest(TestContext ctx) {
        JsonObject issue = IssueRedmine();
        Number issueIdResult = BugTracker.PIVOT.getIssueId(issue);
        ctx.assertNull(issueIdResult);
    }

    @Test
    public void getIssueRedmineTest(TestContext ctx) {
        Number issueIdExpected = 545L;
        JsonObject issue = IssueRedmine();
        Number issueIdResult = BugTracker.REDMINE.getIssueId(issue);
        ctx.assertEquals(issueIdResult, issueIdExpected);
    }

    private JsonObject IssuePassed() {
        return new JsonObject("{\"status\":\"ok\"," +
                "\"message\":\"invalid.action\"," +
                "\"issue\":{\"attribution\":\"RECTORAT\"," +
                "\"id_ent\":\"545\",\"titre\":\"test\"," +
                "\"description\":\"test\"," +
                "\"statut_ent\":\"2\"," +
                "\"demandeur\":\"PRUDON Nathalie | nom.prenom@gmail.com | null | François-Mauriac | 1016024A\"," +
                "\"date_creation\":\"2022-08-11T08:29:33.199\"," +
                "\"academie\":\"CRETEIL\"," +
                "\"modules\":[\"/actualites\"]," +
                "\"source\":\"ENT\",\"date\":\"2022-08-11T08:29:33.253+0000\"," +
                "\"commentaires\":[]," +
                "\"pj\":[]," +
                "\"id_iws\":\"556\"," +
                "\"id_externe\":\"556\"," +
                "\"id_jira\":\"50308\"," +
                "\"statut_jira\":\"Nouveau\"}}");
    }

    private JsonObject IssueRedmine() {
        Number issueId = 545L;
        JsonObject Issue = new JsonObject().put("issue", new JsonObject().put("id",issueId));
        return Issue;
    }
}
