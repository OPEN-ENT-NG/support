package net.atos.entng.support.services;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.services.impl.TicketServiceSqlImpl;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
//@RunWith(PowerMockRunner.class) //Using the PowerMock runner
//@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
//@PrepareForTest({Sql.class}) //Prepare the static class you want to test
//public class TicketServiceSqlImplTest {
//
//    private TicketServiceSql service;
//    private Sql sql;
//
//    @Before
//    public void setup() {
//        this.service = PowerMockito.spy(new TicketServiceSqlImpl(null));
//        this.sql = Mockito.spy(Sql.getInstance());
//        PowerMockito.spy(Sql.class);
//        PowerMockito.when(Sql.getInstance()).thenReturn(sql);
//    }
//    @Test
//    public void listEvents(TestContext ctx) {
//        Async async = ctx.async();
//        UserInfos userInfos = new UserInfos();
//        userInfos.setUserId("userId");
//
//        String expectedQuery = "SELECT username, event, th.status, event_date, user_id, event_type, t.school_id FROM support.tickets_histo th " +
//                " left outer join support.users u on u.id = th.user_id " +
//                " left outer join support.tickets t on th.ticket_id = t.id" +
//                " WHERE ticket_id = ? ";
//        String expectedParams = "[1]";
//
//        Mockito.doAnswer(invocation -> {
//            String query = invocation.getArgument(0);
//            JsonArray values = invocation.getArgument(1);
//            ctx.assertEquals(expectedQuery, query);
//            ctx.assertEquals(expectedParams, values.toString());
//            async.complete();
//            return null;
//        }).when(sql).prepared(Mockito.anyString(), Mockito.any(), Mockito.any());
//
//        this.service.listEvents("1", event -> {});
//        async.awaitSuccess(10000);
//    }
//}
