package ru.practicum.moviehub.http;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Endpoint endpoint = getEndpoint(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getQuery());

        //System.out.println(endpoint.toString());

        switch (endpoint) {
            case GET_ALL:
                getMovies(ex);
                break;
            case POST_ONE:
                putMovie(ex);
                break;
            case GET_ONE:
                getMovie(ex);
                break;
            case DELETE_ONE:
                deleteMovie(ex);
                break;
            case GET_BUNCH:
                getMoviesOfYear(ex);
                break;
            default:
                sendNoContent(ex, 204);
        }
    }

    private void getMovies(HttpExchange ex) throws IOException {
        Gson gson = new Gson();
        List<Movie> listOfMovies = new ArrayList<>(moviesStore.mapToList(moviesStore.getMovies()));
        String json = gson.toJson(listOfMovies);
        sendJson(ex, 200, json);
    }

    private void putMovie(HttpExchange ex) throws IOException {
        Gson gson = new Gson();
        List<String> problems = new ArrayList<>();
        //Если читалка свалится, вернёт нам Опционал нулл
        Optional<String> opJson = movieFromJson(ex);
        if (opJson.isEmpty()) {
            problems.add("Не удалось прочитать данные json");
            ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации", problems);
            String json = gson.toJson(errorResponse);
            sendJson(ex, 422, json);
        } else {
            //Если в Опционале лежат данные, проверяем их тип
            try {
                JsonElement jsElement = JsonParser.parseString(opJson.get());
                boolean titleIsString = jsElement.getAsJsonObject().get("title").getAsJsonPrimitive().isString();
                boolean yearIsNumber = jsElement.getAsJsonObject().get("year").getAsJsonPrimitive().isNumber();
                try { //Читаем заголовок. Если есть проблема, пуляем 415 и выходим из функции
                    String contentType = ex.getRequestHeaders().get("Content-Type").getFirst();
                    if (!contentType.equals("application/json; charset=UTF-8")) { //Если содержимое не то
                        sendNoContent(ex, 415);
                        return;
                    }
                } catch (NullPointerException e) { //Если такого ключа вообще нет
                    sendNoContent(ex, 415);
                    return;
                }
                //Проблемы, не связанные с исключениями
                String title;
                int year;
                if (!titleIsString) {
                    problems.add("Название должно быть строкой");
                } else {
                    title = jsElement.getAsJsonObject().get("title").getAsString();
                    if (title.isEmpty() || title.length() > 100) {
                        problems.add("Название не должно быть пустым и не должно превышать 100 символов");
                    }
                }
                if (!yearIsNumber) {
                    problems.add("Год должен быть числом");
                } else {
                    int currentYear = LocalDate.now().getYear();
                    year = jsElement.getAsJsonObject().get("year").getAsInt();
                    if (year < 1888 || year > (currentYear + 1)) {
                        problems.add("Год должен быть между 1888 и " + (currentYear + 1));
                    }
                }
                //Если проблем нет, обрабатываем запрос
                if (problems.isEmpty()) {
                    //Если на всех этапах проблем не обнаружено, выполняем 201
                    Movie movie = gson.fromJson(opJson.get(), Movie.class);
                    //Movie movie = new Movie(title, year);
                    moviesStore.addMovie(movie);
                    String json = gson.toJson(movie);
                    sendJson(ex, 201, json);
                } else { //Если где-то найдена проблема, отправляем сформированную ошибку
                    ErrorResponse errorResponse = new ErrorResponse("Неверные данные для фильма", problems);
                    String json = gson.toJson(errorResponse);
                    sendJson(ex, 422, json);
                }
            } catch (Exception e) {
                System.err.println("Ошибка преобразования");
                sendNoContent(ex, 204);
            }
        }
    }

    private void getMovie(HttpExchange ex) throws IOException {
        Gson gson = new Gson();
        String[] pathParts = ex.getRequestURI().getPath().split("/");
        try {
            int id = Integer.parseInt(pathParts[2]);
            if (moviesStore.getMovies().containsKey(id)) {
                Movie movie = moviesStore.getMovies().get(id);
                String json = gson.toJson(movie);
                sendJson(ex, 200, json);
            } else {
                String json = gson.toJson("Фильм не найден");
                sendJson(ex, 404, json);
            }
        } catch (NumberFormatException e) {
            String json = gson.toJson("Некорректный ID");
            sendJson(ex, 400, json);
        }
    }

    private void deleteMovie(HttpExchange ex) throws IOException {
        String[] pathParts = ex.getRequestURI().getPath().split("/");
        try {
            int id = Integer.parseInt(pathParts[2]);
            if (moviesStore.getMovies().containsKey(id)) {
                moviesStore.deleteMovie(id);
                sendNoContent(ex, 204);
            } else {
                sendNoContent(ex, 404);
            }
        } catch (NumberFormatException e) {
            sendNoContent(ex, 404);
        }
    }

    private void getMoviesOfYear(HttpExchange ex) throws IOException {
        Gson gson = new Gson();
        String parameter = ex.getRequestURI().getQuery();
        try {
            int year = Integer.parseInt(parameter.substring(parameter.indexOf('=') + 1));

            //Строим новую мапу с фильтром
            Map<Integer, Movie> filteredMovies = new HashMap<>(moviesStore.getMovies()).entrySet().stream()
                    .filter(entry -> entry.getValue().getYear() == year)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            //Делаем из мапы список
            List<Movie> listOfMovies = new ArrayList<>(filteredMovies.values());
            String json = gson.toJson(listOfMovies);
            sendJson(ex, 200, json);
        } catch (NumberFormatException e) {
            String json = gson.toJson("Некорректный параметр запроса year");
            sendJson(ex, 400, json);
        }
    }

    private Endpoint getEndpoint(String requestMethod, String requestPath, String parameters) {
        String[] pathParts = requestPath.split("/");
        if (requestMethod.equalsIgnoreCase("GET") && pathParts.length == 2 && parameters == null) {
            return Endpoint.GET_ALL;
        } else if (requestMethod.equalsIgnoreCase("POST") && pathParts.length == 2 && parameters == null) {
            return Endpoint.POST_ONE;
        } else if (requestMethod.equalsIgnoreCase("GET") && pathParts.length == 3 && parameters == null) {
            return Endpoint.GET_ONE;
        } else if (requestMethod.equalsIgnoreCase("DELETE") && pathParts.length == 3 && parameters == null) {
            return Endpoint.DELETE_ONE;
        } else if (requestMethod.equalsIgnoreCase("GET") && pathParts.length == 2 && parameters.startsWith("year=")) {
            return Endpoint.GET_BUNCH;
        }
        return Endpoint.UNKNOWN;
    }

    enum Endpoint {
        GET_ALL,
        POST_ONE,
        GET_ONE,
        DELETE_ONE,
        GET_BUNCH,
        UNKNOWN
    }
}