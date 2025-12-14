package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080"; // !!! добавьте базовую часть URL
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll //Запускаем сервер
    static void beforeAll() {
        final MoviesStore moviesStore = new MoviesStore();
        moviesStore.addMovie(new Movie("Зверополис 2", 2025));
        moviesStore.addMovie(new Movie("Начало", 2010));
        moviesStore.addMovie(new Movie("Интерстеллар", 2014));
        moviesStore.addMovie(new Movie("Аватар 3", 2025));

        server = new MoviesServer(moviesStore,8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll //Останавливаем сервер
    static void afterAll() {
        server.stop();
    }

    @Test //Всегда массив при попытке получить данные
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test //Возвращаем объект фильма при добавлении фильма
    void resultOfAddMovieIsObject() throws Exception {
        Gson gson = new Gson();
        Movie movie = new Movie("Титаник", 1997);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Статус - 201
        assertEquals(201, response.statusCode(), "POST /movies должен вернуть 201");

        //Мы получили фильм, который передали
        assertEquals(json, response.body(), "Переданные данные должны быть теми же, что полученные");
    }

    @Test //Возвращаем 415, если ContentType неверен
    void codeIs415IfContentTypeUnsupported() throws Exception {
        Gson gson = new Gson();
        Movie movie = new Movie("Титаник", 1997);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                //Передаём фигню в заголовок
                .headers("Content-Type", "application/json; cha SOMECRAP rset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Статус - 415
        assertEquals(415, response.statusCode(), "POST /movies с ошибкой в заголовке должен вернуть 415");
    }

    @Test //Возвращаем объект с информацией об ошибке
    void sendIncorrectMovieReturnObject() throws Exception {
        Gson gson = new Gson();
        Movie movie = new Movie("", 99999999);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Это дcжон объект
        JsonElement jsElement = JsonParser.parseString(response.body());
        assertTrue(jsElement.isJsonObject());

        //Получаем причину
        String reason = jsElement.getAsJsonObject().get("reason").getAsString();
        assertEquals("Неверные данные для фильма", reason);

        //Получаем детали
        String details0 = jsElement.getAsJsonObject().get("details").getAsJsonArray().asList().get(0).getAsString();
        assertEquals("Название не должно быть пустым и не должно превышать 100 символов", details0);

        //Получаем ещё детали
        String details1 = jsElement.getAsJsonObject().get("details").getAsJsonArray().asList().get(1).getAsString();
        assertEquals("Год должен быть между 1888 и " + (LocalDate.now().getYear() + 1), details1);
    }

    @Test //Возвращаем фильм, если нашли фильм
    void returnMovieIfItExist() throws Exception {
        Gson gson = new Gson();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/2"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Это дcжон объект
        JsonElement jsElement = JsonParser.parseString(response.body());
        assertTrue(jsElement.isJsonObject());

        //Это искомый фильм
        Movie movie = gson.fromJson(response.body(), Movie.class);
        assertEquals("Начало", movie.getTitle());
    }

    @Test //Возвращаем 404, если фильма нет (ГЕТ)
    void movieNotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/6"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Фильма с этим ID не нашли (404)
        assertEquals(404, response.statusCode());
    }

    @Test //Возвращаем тело "Некорректный ID", если передан не ID
    void returnBodyIncorrectId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/sdfg"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Это джсон, который строка
        JsonElement jsElement = JsonParser.parseString(response.body());
        String body = jsElement.getAsString();
        assertEquals("Некорректный ID", body);
    }

    @Test //Возвращаем 204, если фильм удалён
    void successfullyDeletedMovie() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/3"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Фильма с этим ID не нашли (404)
        assertEquals(204, response.statusCode());
    }

    @Test //Возвращаем 404, если фильма нет (ДЕЛИТ)
    void return404IfThereIsNoMovieToDelete() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/7"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Фильма с этим ID не нашли (404)
        assertEquals(404, response.statusCode());
    }

    @Test //Возвращаем фильмы при поиске фильмов по году, соответствующие году
    void returnBunchOfMovies() throws Exception {
        Gson gson = new Gson();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2025"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Это дcжон массив
        JsonElement jsElement = JsonParser.parseString(response.body());
        assertTrue(jsElement.isJsonArray());

        Movie movie0 = gson.fromJson(jsElement.getAsJsonArray().get(0), Movie.class);
        Movie movie1 = gson.fromJson(jsElement.getAsJsonArray().get(1), Movie.class);

        //Это действительно фильмы заданного года
        assertEquals(2025, movie0.getYear());
        assertEquals(2025, movie1.getYear());
    }

    @Test //Возвращаем 400 и тело "Некорректный параметр запроса year", если year некорректен
    void return400andBodyIfYearIsIncorrect() throws Exception {
        Gson gson = new Gson();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=dsfhsdf"))
                .headers("Content-Type", "application/json; charset=UTF-8")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        //Код 400
        assertEquals(400, response.statusCode());

        //Это джсон, который строка
        JsonElement jsElement = JsonParser.parseString(response.body());
        String body = jsElement.getAsString();
        assertEquals("Некорректный параметр запроса year", body);
    }
}