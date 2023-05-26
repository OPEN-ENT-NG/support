package net.atos.entng.support.helpers;

import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.model.IModel;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ⚠ If you use this helper you must have the tests that go with it.
 * ⚠ These tests will make it possible to verify the correct implementation of all the classes that implement IModel.
 * ⚠ This will guarantee the correct execution of the line modelClass.getConstructor(JsonObject.class).newInstance(iModel).
 */
public class IModelHelper {
    private final static List<Class<?>> validJsonClasses = Arrays.asList(String.class, boolean.class, Boolean.class,
            double.class, Double.class, float.class, Float.class, Integer.class, int.class, CharSequence.class,
            JsonObject.class, JsonArray.class, Long.class, long.class);
    private final static Logger log = LoggerFactory.getLogger(IModelHelper.class);

    private IModelHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static <T extends IModel<T>> List<T> toList(JsonArray results, Class<T> modelClass) {
        return results.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(iModel -> toModel(iModel, modelClass)).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Generic convert an {@link IModel} to {@link JsonObject}.
     * Classes that do not implement any {@link #validJsonClasses} class or iModel implementation will be ignored.
     * Except {@link List} and {@link Enum}
     *
     * @param ignoreNull If true ignore, fields that are null will not be put in the result
     * @param iModel Instance of {@link IModel} to convert to {@link JsonObject}
     * @return {@link JsonObject}
     */
    public static JsonObject toJson(IModel<?> iModel, boolean ignoreNull, boolean snakeCase) {
        JsonObject statisticsData = new JsonObject();
        final Field[] declaredFields = iModel.getClass().getDeclaredFields();
        Arrays.stream(declaredFields).forEach(field -> {
            boolean accessibility = field.isAccessible();
            field.setAccessible(true);
            try {
                Object object = field.get(iModel);
                String fieldName = snakeCase ? StringHelper.camelToSnake(field.getName()) : field.getName();
                if (object == null) {
                    if (!ignoreNull) statisticsData.putNull(fieldName);
                }
                else if (object instanceof IModel) {
                    statisticsData.put(fieldName, ((IModel<?>)object).toJson());
                } else if (validJsonClasses.stream().anyMatch(aClass -> aClass.isInstance(object))) {
                    statisticsData.put(fieldName, object);
                } else if (object instanceof Enum) {
                    statisticsData.put(fieldName, (Enum) object);
                } else if (object instanceof List) {
                    statisticsData.put(fieldName, listToJsonArray(((List<?>)object)));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            field.setAccessible(accessibility);
        });
        return statisticsData;
    }

    /**
     * Generic convert a list of {@link Object} to {@link JsonArray}.
     * Classes that do not implement any {@link #validJsonClasses} class or iModel implementation will be ignored.
     * Except {@link List} and {@link Enum}
     *
     * @param objects List of object
     * @return {@link JsonArray}
     */
    private static JsonArray listToJsonArray(List<?> objects) {
        JsonArray res = new JsonArray();
        objects.stream()
                .filter(Objects::nonNull)
                .forEach(object -> {
                    if (object instanceof IModel) {
                        res.add(((IModel<?>) object).toJson());
                    }
                    else if (validJsonClasses.stream().anyMatch(aClass -> aClass.isInstance(object))) {
                        res.add(object);
                    } else if (object instanceof Enum) {
                        res.add((Enum)object);
                    } else if (object instanceof List) {
                        res.add(listToJsonArray(((List<?>) object)));
                    }
                });
        return res;
    }

    /**
     * Complete a promise from the result of a sql query, while converting this result into a list of model.
     *
     * @param promise the promise we want to complete
     * @param modelClass the class of the model we want to convert
     * @param errorMessage a message logged when the sql query fail
     * @param <T> the type of the model
     */
    public static <T extends IModel<T>> Handler<Either<String, JsonArray>> sqlResultToIModel(Promise<List<T>> promise, Class<T> modelClass, String errorMessage) {
        return event -> {
            if (event.isLeft()) {
                if (errorMessage != null) {
                    log.error(errorMessage + Ticket.SPACE_CHARACTER + event.left().getValue());
                }
                promise.fail(event.left().getValue());
            } else {
                promise.complete(toList(event.right().getValue(), modelClass));
            }
        };
    }

    public static <T extends IModel<T>> T toModel(JsonObject iModel, Class<T> modelClass) {
        try {
            return modelClass.getConstructor(JsonObject.class).newInstance(iModel);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            return null;
        }
    }
}