package net.atos.entng.support.services;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.services.impl.TicketServiceSqlImpl;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;

import java.util.*;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({Sql.class, TicketServiceSql.class, SqlCrudService.class}) //Prepare the static class you want to test
public class TicketServiceSqlImplTest {

    private TicketServiceSql service;
    private Sql sql;

    @Before
    public void setup() {
        //Permet de créer une instance du service réel tout en mockant certaines de ses méthodes
        this.service = PowerMockito.spy(new TicketServiceSqlImpl(null));
        this.sql = Mockito.spy(Sql.getInstance());
        PowerMockito.spy(Sql.class);
        PowerMockito.when(Sql.getInstance()).thenReturn(sql);
        //sql est une propriété de la classe SqlCrudService et non pas la classe Sql même, il faut donc utiliser whitebox
        //Pour faire changer d'état à sql.
        Whitebox.setInternalState(service, "sql", sql);
    }
    @Test
    public void getListEvents(TestContext ctx) {
        Async async = ctx.async();
        UserInfos userInfos = new UserInfos();
        userInfos.setUserId("userId");

        String expectedQuery = "SELECT username, event, th.status, event_date, user_id, event_type, t.school_id FROM support.tickets_histo th " +
                " left outer join support.users u on u.id = th.user_id " +
                " left outer join support.tickets t on th.ticket_id = t.id" +
                " WHERE ticket_id = ? ";
        String expectedParams = "[1]";

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            JsonArray values = invocation.getArgument(1);
            ctx.assertEquals(expectedQuery, query);
            ctx.assertEquals(expectedParams, values.toString());
            async.complete();
            return null;
        }).when(sql).prepared(Mockito.anyString(), Mockito.any(), Mockito.any());

        this.service.getlistEvents("1");
        async.awaitSuccess(10000);
    }

    @Test
    public void testGetUserTickets(TestContext ctx) {
        Async async = ctx.async();
        UserInfos userInfos = new UserInfos();
        List<String> structures = Collections.singletonList("32acdfaa-dc72-4619-9cdd-a0edacfcfafe");
        userInfos.setStructures(structures);

        UserInfos.Function function = new UserInfos.Function();
        List<String> list = Collections.singletonList("32acdfaa-dc72-4619-9cdd-a0edacfcfafe");
        function.setScope(list);
        Map<String, UserInfos.Function> map = new HashMap<>();
        map.put(DefaultFunctions.ADMIN_LOCAL, function);
        userInfos.setFunctions(map);

        StringBuilder expectedQuery = new StringBuilder();
        expectedQuery.append("SELECT * FROM support.tickets WHERE school_id IN ");
        expectedQuery.append(Sql.listPrepared(userInfos.getStructures()));
        expectedQuery.append(" ORDER BY tickets.id");

        JsonArray expectedParams = new JsonArray(userInfos.getStructures());

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            JsonArray values = invocation.getArgument(1);
            ctx.assertEquals(expectedQuery.toString(), query);
            ctx.assertEquals(expectedParams, values);
            async.complete();
            return null;
        }).when(sql).prepared(Mockito.anyString(), Mockito.any(), Mockito.any());

        this.service.getUserTickets(userInfos);
        async.awaitSuccess(10000);
    }
}
