package net.atos.entng.support.helper;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.support.helpers.TicketEventHelper;
import net.atos.entng.support.helpers.impl.TicketEventHelperImpl;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class TicketEventHelperTest {
    TicketEventHelper helper;
    Map<String, UserInfos.Function> functions;
    JsonArray eventResult;
    JsonObject eventObject;
    List<String> structureList;
    UserInfos user = new UserInfos();

    @Before
    public void setUp() throws NoSuchFieldException {
        helper = new TicketEventHelperImpl();
        functions = new HashMap<>();
        eventResult = new JsonArray();
        eventObject = new JsonObject();
        structureList = new ArrayList<>();
    }

    @Test
    public void testAuthorizeDisplayTicketEventsWhenSuperAdmin(TestContext ctx){
        //User isn't the owner of the ticket and has not the same structure : he's super admin
        //Set user datas : different id, different structure but SUPER ADMIN
        structureList.add("aaaaaa");
        functions.put("SUPER_ADMIN", null);
        user.setStructures(structureList);
        user.setUserId("2222");

        // Set eventResult datas
        eventObject.put("school_id", "school_id");
        eventObject.put("user_id", "11111");
        eventResult.add(eventObject);

        // Execute 
        Boolean result = helper.shouldRenderEvent(user, functions, eventResult);
        ctx.assertEquals(true, result);
    }



    @Test
    public void testAuthorizeDisplayTicketEventsWhenLocalAdmin(TestContext ctx){
        //User is LOCAL ADMIN so he can displays all his structure tickets.
        //Set user datas : different id but same structure AND ADMIN LOCAL
        structureList.add("school_id");
        functions.put("ADMIN_LOCAL", null);
        user.setStructures(structureList);
        user.setUserId("22222");

        // Set eventResult datas
        eventObject.put("school_id", "school_id");
        eventObject.put("user_id", "11111");
        eventResult.add(eventObject);

        // Execute
        Boolean result = helper.shouldRenderEvent(user, functions, eventResult);
        ctx.assertEquals(true, result);
    }

    @Test
    public void testNotAuthorizeDisplayTicketEventsWhenLocalAdmin(TestContext ctx){
        //User is LOCAL ADMIN but he doesn't have the same structure as the ticket.
        //Set user datas : different user id , different structure AND ADMIN LOCAL
        structureList.add("22222");
        functions.put("ADMIN_LOCAL", null);
        user.setStructures(structureList);
        user.setUserId("22222");

        // Set eventResult datas
        eventObject.put("school_id", "school_id");
        eventObject.put("user_id", "11111");
        eventResult.add(eventObject);

        // Execute
        Boolean result = helper.shouldRenderEvent(user, functions, eventResult);
        ctx.assertEquals(false, result);
    }

    @Test
    public void testAuthorizeDisplayTicketEventsWhenOwner(TestContext ctx){
        //Here, the user is the owner of the ticket (same id and structure)
        //Set user datas
        structureList.add("school_id");
        user.setStructures(structureList);
        user.setUserId("11111");

        // Set eventResult datas
        eventObject.put("school_id", "school_id");
        eventObject.put("user_id", "11111");
        eventResult.add(eventObject);

        // Execute
        Boolean result = helper.shouldRenderEvent(user, functions, eventResult);
        ctx.assertEquals(true, result);
    }

    @Test
    public void testNotAuthorizeDisplayTicketEvents(TestContext ctx){
        //Here, user has not function at all and he's not the owner of the ticket.
        //Set user datas
        structureList.add("school_id");
        user.setStructures(structureList);
        user.setUserId("22222");

        // Set eventResult datas
        eventObject.put("school_id", "school_id");
        eventObject.put("user_id", "11111");
        eventResult.add(eventObject);

        // Execute
        Boolean result = helper.shouldRenderEvent(user, functions, eventResult);
        ctx.assertEquals(false, result);
    }

}
